package com.navalrivals.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.security.config.observation.SecurityObservationSettings;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservationPredicate noActuatorObservations(){
        PathMatcher pathMatcher = new AntPathMatcher();
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext serverContext){
                String path = serverContext.getCarrier().getRequestURI();
                return !pathMatcher.match("/actuator/**", path);

            }
            return true;
        };
    }

    @Bean
    public SecurityObservationSettings securityObservationSettings(){
        return SecurityObservationSettings.withDefaults()
                .shouldObserveAuthorizations(false)
                .build();
    }
}
