package com.nankai.yuqing.controller;

import com.nankai.yuqing.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String nickname = Objects.toString(body.get("nickname"), "").trim();
        String password = Objects.toString(body.get("password"), "");
        long retryAfter = authService.retryAfterSeconds(nickname);
        if (retryAfter > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "登录失败次数过多，请在 " + Math.max(1, retryAfter / 60) + " 分钟后重试");
        }
        AuthService.LoginResult login = authService.login(nickname, password);
        if (login == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "昵称或密码错误");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", login.token());
        result.put("nickname", login.nickname());
        result.put("role", login.role());
        result.put("permissions", login.permissions());
        result.put("expiresIn", login.expiresIn());
        return result;
    }

    @PutMapping("/password")
    public Map<String, Object> changePassword(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body) {
        String currentPassword = Objects.toString(body.get("currentPassword"), "");
        String newPassword = Objects.toString(body.get("newPassword"), "");
        if (newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码至少需要 8 个字符");
        }
        if (!authService.changePassword(extractToken(request), currentPassword, newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码错误或会话已失效");
        }
        return Map.of("success", true, "message", "密码修改成功");
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        String nickname = authService.getNickname(extractToken(request));
        if (nickname == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效");
        }
        String token = extractToken(request);
        return Map.of(
            "nickname", nickname,
            "role", authService.getRole(token),
            "permissions", authService.getPermissions(token));
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(HttpServletRequest request) {
        Map<String, Object> profile = authService.getProfile(extractToken(request));
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效");
        }
        return profile;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        authService.logout(extractToken(request));
        return Map.of("success", true);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }
}
