package com.nankai.yuqing.service;

import com.nankai.yuqing.model.SystemSetting;
import com.nankai.yuqing.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** 最小会话认证服务：固定管理员账号 + 内存令牌。 */
@Service
public class AuthService {

    public record LoginResult(String token, String nickname, String role, List<String> permissions, long expiresIn) {}
    private record Session(String nickname, String role, Instant createdAt, Instant lastActiveAt, Instant expiresAt) {}

    public static final String ADMIN_ROLE = "ADMIN";
    public static final List<String> ADMIN_PERMISSIONS = List.of(
        "VIEW_DATA", "REVIEW_POST", "MANAGE_EVENT", "MANAGE_SETTINGS", "MANAGE_DATA", "VIEW_AUDIT");

    private final String configuredNickname;
    private final String configuredPassword;
    private final Duration sessionDuration;
    private final SystemSettingRepository settingRepository;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int PBKDF2_KEY_BITS = 256;
    private record LoginAttempt(int count, Instant lockedUntil) {}

    @Autowired
    public AuthService(
            @Value("${yuqing.auth.nickname:管理员}") String configuredNickname,
            @Value("${yuqing.auth.password:}") String configuredPassword,
            @Value("${yuqing.auth.session-hours:24}") long sessionHours,
            SystemSettingRepository settingRepository) {
        this.configuredNickname = configuredNickname;
        this.configuredPassword = configuredPassword;
        this.sessionDuration = Duration.ofHours(Math.max(1, sessionHours));
        this.settingRepository = settingRepository;
    }

    /** 供单元测试使用；生产环境由 Spring 注入持久化仓库。 */
    public AuthService(String configuredNickname, String configuredPassword, long sessionHours) {
        this.configuredNickname = configuredNickname;
        this.configuredPassword = configuredPassword;
        this.sessionDuration = Duration.ofHours(Math.max(1, sessionHours));
        this.settingRepository = null;
    }

    public LoginResult login(String nickname, String password) {
        if (retryAfterSeconds(nickname) > 0) return null;
        if (!configuredNickname.equals(nickname) || !passwordMatches(password)) {
            recordLoginFailure(nickname);
            return null;
        }
        loginAttempts.remove(nickname);
        removeExpiredSessions();
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        Session session = new Session(nickname, ADMIN_ROLE, now, now, now.plus(sessionDuration));
        sessions.put(token, session);
        persistSession(token, session);
        return new LoginResult(token, nickname, ADMIN_ROLE, ADMIN_PERMISSIONS, sessionDuration.toSeconds());
    }

    public long retryAfterSeconds(String nickname) {
        LoginAttempt attempt = loginAttempts.get(nickname);
        if (attempt == null || attempt.lockedUntil() == null) return 0;
        long seconds = Duration.between(Instant.now(), attempt.lockedUntil()).toSeconds();
        if (seconds <= 0) {
            loginAttempts.remove(nickname);
            return 0;
        }
        return seconds;
    }

    public boolean changePassword(String token, String currentPassword, String newPassword) {
        if (validSession(token) == null || !passwordMatches(currentPassword) || newPassword == null || newPassword.length() < 8) {
            return false;
        }
        if (settingRepository == null) return false;
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String encodedSalt = java.util.Base64.getEncoder().encodeToString(salt);
        String encodedHash = java.util.Base64.getEncoder().encodeToString(
            pbkdf2(newPassword, salt, PBKDF2_ITERATIONS));
        settingRepository.save(new SystemSetting("auth.password.hash",
            "pbkdf2$" + PBKDF2_ITERATIONS + "$" + encodedSalt + "$" + encodedHash));
        sessions.entrySet().removeIf(entry -> !entry.getKey().equals(token));
        return true;
    }

    public String getNickname(String token) {
        if (token == null || token.isBlank()) return null;
        Session session = sessions.computeIfAbsent(token, this::loadSession);
        if (session == null) return null;
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        return session.nickname();
    }

    public boolean isValid(String token) {
        return getNickname(token) != null;
    }

    public String getRole(String token) {
        Session session = validSession(token);
        return session == null ? null : session.role();
    }

    public List<String> getPermissions(String token) {
        return ADMIN_ROLE.equals(getRole(token)) ? ADMIN_PERMISSIONS : List.of("VIEW_DATA");
    }

    public boolean hasPermission(String token, String permission) {
        return getPermissions(token).contains(permission);
    }

    public Map<String, Object> getProfile(String token) {
        Session session = validSession(token);
        if (session == null) return null;
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("nickname", session.nickname());
        profile.put("role", session.role());
        profile.put("roleLabel", ADMIN_ROLE.equals(session.role()) ? "系统管理员" : session.role());
        profile.put("accountType", "本地管理账号");
        profile.put("permissions", getPermissions(token));
        profile.put("createdAt", localTime(session.createdAt()));
        profile.put("lastActiveAt", localTime(session.lastActiveAt()));
        profile.put("expiresAt", localTime(session.expiresAt()));
        profile.put("remainingSeconds", Math.max(0, Duration.between(Instant.now(), session.expiresAt()).toSeconds()));
        profile.put("sessionHours", sessionDuration.toHours());
        return profile;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
            deletePersistedSession(token);
        }
    }

    private void removeExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private Session validSession(String token) {
        if (token == null || token.isBlank()) return null;
        Session session = sessions.computeIfAbsent(token, this::loadSession);
        if (session == null) return null;
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            deletePersistedSession(token);
            return null;
        }
        Session active = new Session(
            session.nickname(), session.role(), session.createdAt(), Instant.now(), session.expiresAt());
        sessions.put(token, active);
        if (Duration.between(session.lastActiveAt(), active.lastActiveAt()).toMinutes() >= 5) {
            persistSession(token, active);
        }
        return active;
    }

    private boolean passwordMatches(String password) {
        if (password == null) return false;
        if (settingRepository != null) {
            String stored = settingRepository.findById("auth.password.hash").map(SystemSetting::getValue).orElse("");
            if (stored.startsWith("pbkdf2$")) return matchesPbkdf2(password, stored);
            // 兼容早期版本的 salt:sha256 存量密码；下次修改密码后自动使用 PBKDF2。
            String[] parts = stored.split(":", 2);
            if (parts.length == 2) return MessageDigest.isEqual(
                parts[1].getBytes(StandardCharsets.UTF_8),
                hash(parts[0] + password).getBytes(StandardCharsets.UTF_8));
        }
        return configuredPassword != null && !configuredPassword.isBlank()
            && MessageDigest.isEqual(
                configuredPassword.getBytes(StandardCharsets.UTF_8),
                password.getBytes(StandardCharsets.UTF_8));
    }

    private boolean matchesPbkdf2(String password, String stored) {
        try {
            String[] parts = stored.split("\\$", 4);
            if (parts.length != 4) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 1_000_000) return false;
            byte[] salt = java.util.Base64.getDecoder().decode(parts[2]);
            byte[] expected = java.util.Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算密码摘要", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private void recordLoginFailure(String nickname) {
        loginAttempts.compute(nickname, (key, old) -> {
            int count = old == null ? 1 : old.count() + 1;
            return new LoginAttempt(count, count >= MAX_LOGIN_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null);
        });
    }

    private String sessionKey(String token) {
        return "auth.session." + hash(token).substring(0, 48);
    }

    private void persistSession(String token, Session session) {
        if (settingRepository == null) return;
        String value = String.join("|", session.nickname(), session.role(),
            String.valueOf(session.createdAt().toEpochMilli()),
            String.valueOf(session.lastActiveAt().toEpochMilli()),
            String.valueOf(session.expiresAt().toEpochMilli()));
        settingRepository.save(new SystemSetting(sessionKey(token), value));
    }

    private Session loadSession(String token) {
        if (settingRepository == null) return null;
        try {
            String[] values = settingRepository.findById(sessionKey(token))
                .map(SystemSetting::getValue).orElse("").split("\\|");
            if (values.length != 5) return null;
            Session session = new Session(values[0], values[1],
                Instant.ofEpochMilli(Long.parseLong(values[2])),
                Instant.ofEpochMilli(Long.parseLong(values[3])),
                Instant.ofEpochMilli(Long.parseLong(values[4])));
            if (session.expiresAt().isBefore(Instant.now())) {
                deletePersistedSession(token);
                return null;
            }
            return session;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void deletePersistedSession(String token) {
        if (settingRepository != null) settingRepository.deleteById(sessionKey(token));
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算安全摘要", ex);
        }
    }

    private LocalDateTime localTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }
}
