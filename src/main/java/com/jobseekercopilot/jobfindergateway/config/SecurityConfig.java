package com.jobseekercopilot.jobfindergateway.config;

import com.jobseekercopilot.jobfindergateway.security.JobFinderAuthenticationEntryPoint;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JobFinderAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/jobs/**").authenticated()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(authenticationEntryPoint))
                .build();
    }

    @Bean
    JwtDecoder jobFinderJwtDecoder(
            @Value("${job-finder.security.jwk-set-uri}") String jwkSetUri,
            @Value("${job-finder.security.issuer}") String issuer,
            @Value("${job-finder.security.audience}") String audience) {
        validateConfiguration(jwkSetUri, issuer, audience);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                requiredAudience(audience),
                requiredAccessToken()));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> requiredAudience(String audience) {
        return token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : invalidToken();
    }

    private static OAuth2TokenValidator<Jwt> requiredAccessToken() {
        return token -> token.getSubject() != null
                        && !token.getSubject().isBlank()
                        && "access".equals(token.getClaimAsString("token_type"))
                ? OAuth2TokenValidatorResult.success()
                : invalidToken();
    }

    private static OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Access token validation failed.", null));
    }

    static void validateConfiguration(String jwkSetUri, String issuer, String audience) {
        try {
            URI uri = URI.create(jwkSetUri);
            if (!List.of("http", "https").contains(uri.getScheme())
                    || !uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || issuer == null
                    || issuer.isBlank()
                    || audience == null
                    || audience.isBlank()) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Job Finder JWT verification configuration is invalid");
        }
    }
}
