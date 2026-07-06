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

    /** Analyst can access these API prefixes */
    private static final Set<String> ANALYST_ALLOWED = new HashSet<>(Arrays.asList(
            "/api/dashboard", "/api/transaction", "/api/alert", "/api/metrics",
            "/api/blacklist", "/api/chain"));

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

        // Analyst: check allowed paths
        if (Constants.ROLE_ANALYST.equals(role)) {
            boolean allowed = false;
            for (String prefix : ANALYST_ALLOWED) {
                if (path.startsWith(prefix)) { allowed = true; break; }
            }
            if (!allowed) {
                writeJson(response, 403, "风控分析师无权访问此接口");
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

        // Admin: full access, no additional checks

        request.setAttribute("username", username);
        request.setAttribute("role", role);
        return true;
    }

    private void writeJson(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(
                Result.fail(code, msg)));
    }
}
