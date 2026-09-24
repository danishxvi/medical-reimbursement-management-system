package com.mrms.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the caller's IP address. X-Forwarded-For is deliberately not
 * parsed here: when the app runs behind the reverse proxy, the container's
 * forward header support (server.forward-headers-strategy=native) rewrites
 * the remote address from trusted proxies only, so a client cannot spoof it.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return of(attrs.getRequest());
        }
        return null;
    }
}
