package com.nankai.yuqing.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    @Test
    void adminLoginReturnsRoleAndDataManagementPermission() {
        AuthService service = new AuthService("管理员", "123456", 24);

        AuthService.LoginResult result = service.login("管理员", "123456");

        assertNotNull(result);
        assertEquals("ADMIN", result.role());
        assertTrue(result.permissions().contains("MANAGE_DATA"));
        assertTrue(service.hasPermission(result.token(), "VIEW_AUDIT"));
    }

    @Test
    void invalidCredentialsDoNotCreateSession() {
        AuthService service = new AuthService("管理员", "123456", 24);

        assertNull(service.login("管理员", "错误密码"));
        assertFalse(service.isValid("missing-token"));
        assertFalse(service.hasPermission("missing-token", "MANAGE_DATA"));
    }

    @Test
    void blankDeploymentPasswordNeverActsAsAPublicDefault() {
        AuthService service = new AuthService("管理员", "", 24);

        assertNull(service.login("管理员", "123456"));
        assertNull(service.login("管理员", ""));
    }
}
