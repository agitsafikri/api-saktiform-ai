package com.saktiform.api.util;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtil {
    private IpUtil() {}

    /**
     * Resolves the client IP using the precedence:
     * CF-Connecting-IP -> first IP of X-Forwarded-For -> getRemoteAddr().
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Forwarded-For");
        }
        if (ip != null && !ip.isEmpty()) {
            // X-Forwarded-For may be "client, proxy1, proxy2" - take the first (client)
            int comma = ip.indexOf(',');
            if (comma > -1) {
                ip = ip.substring(0, comma);
            }
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
