package com.finance.risk.dashboard.config;

import com.alibaba.fastjson.JSON;
import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.common.JwtUtil;
import com.finance.risk.dashboard.vo.Result;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** Real-time analysts, including pre-migration analyst accounts, have read-only access. */
    private static final Set<String> REALTIME_ANALYST_ALLOWED = new HashSet<>(Arrays.asList(
            "/api/dashboard", "/api/transaction", "/api/alert", "/api/metrics"));

    /** Offline analysts can read offline outputs and start the standard offline job. */
    private static final Set<String> OFFLINE_ANALYST_ALLOWED = new HashSet<>(Arrays.asList(
            "/api/offline", "/api/profile", "/api/device-risk", "/api/counterparty-risk",
            "/api/transaction-stats", "/api/blacklist", "/api/chain"));

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeJson(response, 401, "未登录，请先登录");
            return false;
        }

        String token = header.substring(7);
        if (!JwtUtil.validate(token)) {
            writeJson(response, 401, "登录已过期，请重新登录");
            return false;
        }

        String username = JwtUtil.getUsername(token);
        String role = JwtUtil.getRole(token);
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Trader: no API access (only auth endpoints, which are excluded from interception)
        if (Constants.ROLE_TRADER.equals(role)) {
            writeJson(response, 403, "普通用户无权访问风控系统");
            return false;
        }

        boolean realtimeAnalyst = Constants.ROLE_REALTIME_ANALYST.equals(role)
                || Constants.ROLE_ANALYST.equals(role);
        if (realtimeAnalyst) {
            if (!hasAllowedPrefix(path, REALTIME_ANALYST_ALLOWED) || !"GET".equalsIgnoreCase(method)) {
                writeJson(response, 403, "实时分析师仅有实时模块的只读权限");
                return false;
            }
        }

        if (Constants.ROLE_OFFLINE_ANALYST.equals(role)) {
            boolean mayControlAnalysis = "POST".equalsIgnoreCase(method)
                    && ("/api/offline/analyze/recent-30-days".equals(path)
                    || "/api/offline/analyze/pause".equals(path)
                    || "/api/offline/analyze/resume".equals(path)
                    || "/api/offline/analyze/cancel".equals(path));
            if (!hasAllowedPrefix(path, OFFLINE_ANALYST_ALLOWED)
                    || (!"GET".equalsIgnoreCase(method) && !mayControlAnalysis)) {
                writeJson(response, 403, "离线分析师仅有离线模块的分析和只读权限");
                return false;
            }
        }

        // Auditor: read-only
        if (Constants.ROLE_AUDITOR.equals(role)) {
            if (!"GET".equalsIgnoreCase(method)) {
                writeJson(response, 403, "审计员仅有只读权限");
                return false;
            }
        }

        if (!Constants.ROLE_ADMIN.equals(role)
                && !Constants.ROLE_AUDITOR.equals(role)
                && !Constants.ROLE_TRADER.equals(role)
                && !realtimeAnalyst
                && !Constants.ROLE_OFFLINE_ANALYST.equals(role)) {
            writeJson(response, 403, "未知角色，无权访问系统");
            return false;
        }

        // Admin: full access, no additional checks

        request.setAttribute("username", username);
        request.setAttribute("role", role);
        return true;
    }

    private boolean hasAllowedPrefix(String path, Set<String> allowedPrefixes) {
        for (String prefix : allowedPrefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void writeJson(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(
                Result.fail(code, msg)));
    }
}
