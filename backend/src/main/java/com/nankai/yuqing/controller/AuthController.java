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
        AuthService.LoginResult login = authService.login(nickname, password);
        if (login == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "昵称或密码错误");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", login.token());
        result.put("nickname", login.nickname());
        result.put("expiresIn", login.expiresIn());
        return result;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        String nickname = authService.getNickname(extractToken(request));
        if (nickname == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效");
        }
        return Map.of("nickname", nickname);
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
