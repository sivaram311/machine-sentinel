package com.delena.machinesentinel.security;

import com.delena.machinesentinel.config.SentinelProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String[] PUBLIC = {
            "/api/health", "/actuator/health", "/error"
    };

    @Bean
    @ConditionalOnProperty(name = "sentinel.security.enabled", havingValue = "false")
    public SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
        log.warn("SECURITY: sentinel.security.enabled=false — API open (local break-glass only)");
        http.cors(c -> c.configurationSource(corsConfigurationSource(null)))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "sentinel.security.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain jwksFilterChain(HttpSecurity http, SentinelProperties props) throws Exception {
        var sec = props.security();
        log.info("SECURITY: CSS JWKS enabled (jwkSetUri={}, clientId={})", sec.jwkSetUri(), sec.clientId());
        http.cors(c -> c.configurationSource(corsConfigurationSource(props)))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder(props))))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    private JwtDecoder jwtDecoder(SentinelProperties props) {
        String clientId = props.security().clientId();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.security().jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> {
            List<String> aud = jwt.getAudience();
            boolean audMatch = aud != null && aud.contains(clientId);
            boolean clientMatch = clientId.equals(jwt.getClaimAsString("client_id"))
                    || clientId.equals(jwt.getClaimAsString("azp"));
            if (audMatch || clientMatch) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "Required audience/client " + clientId + " not present", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), audience));
        return decoder;
    }

    private CorsConfigurationSource corsConfigurationSource(SentinelProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        List<String> origins = props != null && props.security() != null && props.security().corsOrigins() != null
                ? props.security().corsOrigins()
                : List.of("http://127.0.0.1:3351", "http://localhost:3351");
        c.setAllowedOriginPatterns(origins);
        c.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
