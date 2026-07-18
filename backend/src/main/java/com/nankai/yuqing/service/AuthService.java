package com.nankai.yuqing.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 最小会话认证服务：固定管理员账号 + 内存令牌。 */
@Service
public class AuthService {

    public record LoginResult(String token, String nickname, long expiresIn) {}
    private record Session(String nickname, Instant expiresAt) {}

    private final String configuredNickname;
    private final String configuredPassword;
    private final Duration sessionDuration;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthService(
            @Value("${yuqing.auth.nickname:管理员}") String configuredNickname,
            @Value("${yuqing.auth.password:123456}") String configuredPassword,
            @Value("${yuqing.auth.session-hours:24}") long sessionHours) {
        this.configuredNickname = configuredNickname;
        this.configuredPassword = configuredPassword;
        this.sessionDuration = Duration.ofHours(Math.max(1, sessionHours));
    }

    public LoginResult login(String nickname, String password) {
        if (!configuredNickname.equals(nickname) || !configuredPassword.equals(password)) {
            return null;
        }
        removeExpiredSessions();
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(nickname, Instant.now().plus(sessionDuration)));
        return new LoginResult(token, nickname, sessionDuration.toSeconds());
    }

    public String getNickname(String token) {
        if (token == null || token.isBlank()) return null;
        Session session = sessions.get(token);
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

    public void logout(String token) {
        if (token != null) sessions.remove(token);
    }

    private void removeExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
