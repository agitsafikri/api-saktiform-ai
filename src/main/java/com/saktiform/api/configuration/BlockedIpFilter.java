package com.saktiform.api.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saktiform.api.model.RestResponse;
import com.saktiform.api.service.BlockedIpService;
import com.saktiform.api.util.IpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class BlockedIpFilter extends OncePerRequestFilter {

    private final BlockedIpService blockedIpService;
    private final ObjectMapper objectMapper;

    public BlockedIpFilter(BlockedIpService blockedIpService, ObjectMapper objectMapper) {
        this.blockedIpService = blockedIpService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ip = IpUtil.resolveClientIp(request);

        // fail-closed: deny if the IP cannot be resolved or is blocked
        if (ip == null || ip.isBlank() || blockedIpService.isBlocked(ip)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(new RestResponse(false, "Akses ditolak")));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
