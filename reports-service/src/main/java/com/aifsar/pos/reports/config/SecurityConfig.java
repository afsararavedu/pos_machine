package com.aifsar.pos.reports.config;

import com.aifsar.pos.common.security.JwtSecuritySupport;
import com.aifsar.pos.common.web.CorrelationIdFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                .requestMatchers("/api/v1/reports/**").hasAnyRole("ADMIN", "AUDITOR", "MERCHANT")
                .anyRequest().denyAll())
            .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(JwtSecuritySupport.rolesConverter())));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${security.jwt.jwk-set-uri}") String jwkSetUri,
                          @Value("${security.jwt.issuer}") String issuer,
                          @Value("${security.jwt.audience}") String audience) {
        return JwtSecuritySupport.jwkSetDecoder(jwkSetUri, issuer, audience);
    }

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> bean = new FilterRegistrationBean<>(new CorrelationIdFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
