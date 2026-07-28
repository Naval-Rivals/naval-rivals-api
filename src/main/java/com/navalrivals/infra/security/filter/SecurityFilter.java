package com.navalrivals.infra.security.filter;

import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import com.navalrivals.infra.security.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        try{
            var token = recoverToken(request);

            if(token != null){
                var subject = tokenService.validateToken(token);
                var user = userRepository.findByEmail(subject)
                        .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
                var userLog = (User) user;
                MDC.put("userId", userLog.getId().toString());
                var authorization = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authorization);
                log.debug("[AUTH] Token válido para userId={} — {} {}", userLog.getId(), request.getMethod(), request.getRequestURI());
            }
            filterChain.doFilter(request, response);
        } catch (Exception e){
            log.warn("[AUTH] Token inválido ou expirado — {} {} — motivo: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("""
                {"message" : "Token inválido ou expirado"}
            """);
        } finally {
            MDC.remove("userId");
        }
    }

    private String recoverToken(HttpServletRequest request) {

        var authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null){
            return authorizationHeader.replace("Bearer ", "");
        }
        return null;
    }
}
