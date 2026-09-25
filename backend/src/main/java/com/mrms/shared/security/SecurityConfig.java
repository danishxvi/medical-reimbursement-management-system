package com.mrms.shared.security;

import com.mrms.shared.config.MrmsProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Central web security configuration.
 *
 * <ul>
 *   <li>Server side sessions in an HttpOnly, Secure, SameSite=Strict cookie
 *       (no tokens in browser storage, sessions can be revoked instantly).</li>
 *   <li>CSRF protection with the double submit cookie pattern.</li>
 *   <li>One active session per account; a new login ends the older one.</li>
 *   <li>Argon2id password hashing behind a delegating encoder so the
 *       algorithm can be upgraded later without a mass reset.</li>
 *   <li>Strict security headers on every response.</li>
 *   <li>Per IP rate limiting and forced change of temporary passwords.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Microsecond precision clock: databases store microseconds, so values
     * returned to clients always equal what was persisted.
     */
    @Bean
    Clock clock() {
        return new MicrosecondClock(Clock.systemUTC());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // OWASP recommended Argon2id parameters: 19 MiB memory, 2 iterations, 1 lane
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2));
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Lets the session registry learn when sessions expire or are invalidated. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(MrmsProperties props) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .sameSite("Strict")
                .secure(props.csrf().secureCookie())
                .path("/"));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Applied by the login endpoint after a successful password check:
     * limits concurrent sessions, rotates the session id (prevents session
     * fixation), registers the session and rotates the CSRF token.
     */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry registry,
                                                                CookieCsrfTokenRepository csrfRepository) {
        ConcurrentSessionControlAuthenticationStrategy concurrency =
                new ConcurrentSessionControlAuthenticationStrategy(registry);
        concurrency.setMaximumSessions(1);
        concurrency.setExceptionIfMaximumExceeded(false);
        // Issue the rotated CSRF token in the login response itself
        CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(csrfRepository);
        csrf.setRequestHandler(new SpaCsrfTokenRequestHandler());
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrency,
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(registry),
                csrf));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            MrmsProperties props,
                                            Clock clock,
                                            SessionRegistry sessionRegistry,
                                            CookieCsrfTokenRepository csrfRepository,
                                            SecurityContextRepository contextRepository,
                                            ObjectProvider<LogoutHandler> logoutHandlers) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsSource(props)))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(ctx -> ctx.securityContextRepository(contextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(1)
                                .sessionRegistry(sessionRegistry)
                                .expiredSessionStrategy(event -> writeProblem(event.getResponse(), 401,
                                        "SESSION_REPLACED",
                                        "You were signed out because your account signed in elsewhere"))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/legal/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Role rules by URL as a second line of defence (methods are also annotated).
                        // They also stop request body validation from running for the wrong role.
                        .requestMatchers(HttpMethod.POST, "/api/documents", "/api/nac", "/api/claims").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.PUT, "/api/claims/*").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.DELETE, "/api/claims/*").hasRole("EMPLOYEE")
                        .requestMatchers("/api/profile/**").hasRole("EMPLOYEE")
                        .requestMatchers("/api/budget/school/**").hasRole("HOS")
                        .requestMatchers("/api/budget/pao/**").hasAnyRole("PAO_AUDITOR", "PAO_OFFICER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .requestCache(cache -> cache.disable())
                .logout(logout -> {
                    logout.logoutUrl("/api/auth/logout")
                            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler())
                            .deleteCookies("MRMS_SESSION")
                            .invalidateHttpSession(true)
                            .clearAuthentication(true);
                    logoutHandlers.orderedStream().forEach(logout::addLogoutHandler);
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeProblem(res, 401, "UNAUTHENTICATED", "Please sign in to continue"))
                        .accessDeniedHandler((req, res, e) -> {
                            if (e instanceof CsrfException) {
                                writeProblem(res, 403, "CSRF_INVALID",
                                        "Your session security token expired. Please reload the page");
                            } else {
                                writeProblem(res, 403, "FORBIDDEN", "You are not allowed to perform this action");
                            }
                        }))
                .headers(headers -> headers
                        // The API never serves HTML, so nothing may be loaded or framed
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000))
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin")))
                .addFilterBefore(new RateLimitFilter(
                        props.security().loginRequestsPerMinute(),
                        props.security().apiRequestsPerMinute(),
                        clock), CsrfFilter.class)
                .addFilterAfter(new PasswordChangeEnforcementFilter(), AuthorizationFilter.class);

        return http.build();
    }

    private static CorsConfigurationSource corsSource(MrmsProperties props) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = props.cors() == null ? List.of() : props.cors().allowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
            config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }

    static void writeProblem(HttpServletResponse response, int status, String code, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code
                + "\",\"detail\":\"" + detail + "\"}");
    }
}
