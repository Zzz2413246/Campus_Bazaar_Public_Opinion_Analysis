package com.nankai.yuqing.config;

import com.nankai.yuqing.service.AuthService;
import com.nankai.yuqing.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final AuditService auditService;

    public AuthInterceptor(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ")
            ? header.substring(7).trim()
            : null;
        if (authService.isValid(token)) {
            request.setAttribute("auditOperator", authService.getNickname(token));
            request.setAttribute("auditRole", authService.getRole(token));
            if (requiresDataManagement(request) && !authService.hasPermission(token, "MANAGE_DATA")) {
                writeForbidden(response, "当前账号没有数据管理权限");
                return false;
            }
            if (requiresSettingsManagement(request) && !authService.hasPermission(token, "MANAGE_SETTINGS")) {
                writeForbidden(response, "当前账号没有系统设置权限");
                return false;
            }
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"请先登录\"}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (!isAuditedMutation(request)) return;
        AuditAction action = auditAction(request);
        auditService.record(
            String.valueOf(request.getAttribute("auditOperator")),
            String.valueOf(request.getAttribute("auditRole")),
            action.code(), action.name(), action.targetType(), action.targetId(),
            request.getMethod() + " " + request.getRequestURI(),
            ex == null && response.getStatus() < 400 ? "成功" : "失败",
            clientIp(request));
    }

    private boolean requiresDataManagement(HttpServletRequest request) {
        if ("GET".equalsIgnoreCase(request.getMethod())) return false;
        String path = request.getRequestURI();
        return path.startsWith("/api/data") || path.startsWith("/api/analysis/extensions");
    }

    private boolean requiresSettingsManagement(HttpServletRequest request) {
        return !"GET".equalsIgnoreCase(request.getMethod())
            && request.getRequestURI().startsWith("/api/settings");
    }

    private boolean isAuditedMutation(HttpServletRequest request) {
        if ("GET".equalsIgnoreCase(request.getMethod()) || "OPTIONS".equalsIgnoreCase(request.getMethod())) return false;
        String path = request.getRequestURI();
        return path.startsWith("/api/posts") || path.startsWith("/api/events")
            || path.startsWith("/api/settings") || path.startsWith("/api/data")
            || path.startsWith("/api/analysis/extensions") || path.startsWith("/api/auth/logout");
    }

    private AuditAction auditAction(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/review/batch")) return new AuditAction("POST_BATCH_REVIEW", "批量复核帖子", "帖子", "批量");
        if (path.contains("/review")) return new AuditAction("POST_REVIEW", "人工复核帖子", "帖子", segmentBefore(path, "review"));
        if (path.startsWith("/api/events")) return new AuditAction("EVENT_UPDATE", "更新事件处置", "事件", segmentBefore(path, "status"));
        if (path.startsWith("/api/settings")) return new AuditAction("SETTINGS_UPDATE", "修改系统设置", "系统设置", "analysis");
        if (path.contains("/comments/import")) return new AuditAction("COMMENT_IMPORT", "导入评论数据", "数据", "comments");
        if (path.endsWith("/import")) return new AuditAction("POST_IMPORT", "导入帖子数据", "数据", "posts");
        if (path.endsWith("/reanalyze")) return new AuditAction("DATA_REANALYZE", "重新分析全部数据", "数据", "all");
        if (path.endsWith("/all")) return new AuditAction("DATA_CLEAR", "清空全部数据", "数据", "all");
        if (path.startsWith("/api/analysis/extensions")) return new AuditAction("EXTENSION_RUN", "运行扩展分析", "分析任务", path.substring(path.lastIndexOf('/') + 1));
        return new AuditAction("LOGOUT", "退出登录", "会话", "current");
    }

    private String segmentBefore(String path, String suffix) {
        String value = path.substring(0, path.lastIndexOf('/' + suffix));
        return value.substring(value.lastIndexOf('/') + 1);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String address = forwarded == null || forwarded.isBlank()
            ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1").contains(address) ? "本机" : "外部来源";
    }

    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"" + message + "\"}");
    }

    private record AuditAction(String code, String name, String targetType, String targetId) {}
}
