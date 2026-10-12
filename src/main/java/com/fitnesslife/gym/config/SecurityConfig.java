package com.fitnesslife.gym.config;

import java.io.IOException;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fitnesslife.gym.service.CustomUserDetailsService;
import com.fitnesslife.gym.service.LoginAttemptService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private LoginFailureHandler loginFailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index", "/login", "/register",
                                "/css/**", "/js/**", "/img/**", "/fonts/**",
                                "/payment/confirmation", "/payment/response",
                                "/ws/**", "/api/password/**", "/legal/**")
                        .permitAll()

                        .requestMatchers("/dashboard/**", "/api/dashboard/**", "/admin/**").hasRole("ADMIN")
                        .requestMatchers("/update-role").hasRole("ADMIN")

                        .requestMatchers("/trainings/inscribirme/**", "/trainings/cancelarInscripcion/**")
                        .authenticated()
                        .requestMatchers("/trainings/**").hasRole("ADMIN")

                        .requestMatchers("/api/churn/**").hasRole("ADMIN")
                        .requestMatchers("/attendance/kiosk").hasRole("ADMIN")
                        .requestMatchers("/api/attendance/verify").hasRole("ADMIN")
                        .requestMatchers("/api/attendance/active-count").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/trainer/**").hasRole("TRAINER")
                        .requestMatchers("/api/plan/**").hasRole("USER")

                        .anyRequest().authenticated())

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            loginAttemptService.recordSuccess(authentication.getName());

                            var authorities = authentication.getAuthorities();

                            if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                                response.sendRedirect(request.getContextPath() + "/dashboard");
                                return;
                            }

                            if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER"))) {
                                response.sendRedirect(request.getContextPath() + "/home");
                                return;
                            }

                            response.sendRedirect(request.getContextPath() + "/home");
                        })
                        .failureHandler(loginFailureHandler)
                        .permitAll())

                .sessionManagement(session -> session
                        .invalidSessionUrl("/login?expired=true")
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .sessionRegistry(sessionRegistry()))

                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll())

                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/payment/confirmation"))

                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    private static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
        private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                Supplier<CsrfToken> csrfToken) {
            this.delegate.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (headerValue != null && !headerValue.isBlank())
                    ? super.resolveCsrfTokenValue(request, csrfToken)
                    : this.delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            csrfToken.getToken();
            filterChain.doFilter(request, response);
        }
    }

    @Bean
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http
                .getSharedObject(AuthenticationManagerBuilder.class);

        authenticationManagerBuilder
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder());

        return authenticationManagerBuilder.build();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}