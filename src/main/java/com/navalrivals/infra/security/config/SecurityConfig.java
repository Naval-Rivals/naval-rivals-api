package com.navalrivals.infra.security.config;

import com.navalrivals.infra.exception.exceptions.SecurityException;
import com.navalrivals.infra.security.filter.SecurityFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityFilter securityFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http){
        try {
            return http
                    .cors(cors -> {})
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> {
                        authorize.requestMatchers("/ws/**").permitAll();
                        authorize.requestMatchers(HttpMethod.POST, "/auth/**").permitAll();
                        authorize.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                        authorize.requestMatchers(HttpMethod.GET, "/users/me").authenticated();
                        authorize.requestMatchers(HttpMethod.GET, "/users/*").permitAll();
                        authorize.anyRequest().authenticated();
                    })
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint())
                    )
                    .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }catch (Exception e){
            throw new SecurityException("Erro interno na configuração de segurança");
        }
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");

            response.getWriter().write("""
                {"message": "Token não fornecido ou inválido"}
            """);
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration){
        try {
            return authenticationConfiguration.getAuthenticationManager();
        } catch (Exception e) {
            throw new SecurityException("Erro interno na configuração de segurança");
        }
    }
}
