package com.smashit_beta.backend.config;

import com.smashit_beta.backend.security.UserDetailsServiceImpl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    // Configure which frontend origins can communicate with this backend.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // Vite's usual local development address.
        configuration.setAllowedOrigins(
                List.of("http://localhost:5173")
        );

        // Allow the HTTP methods our API will use.
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );

        // Allow JSON requests and the CSRF header.
        configuration.setAllowedHeaders(
                List.of("Content-Type", "X-XSRF-TOKEN")
        );

        // Required when the browser sends session cookies.
        configuration.setAllowCredentials(true);

        // Apply these CORS rules to all API routes.
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    // NEW: decides WHERE a logged-in user's security info is stored between requests.
    // HttpSessionSecurityContextRepository stores it in the server-side HTTP session
    // (identified by the JSESSIONID cookie). It is a bean so that this filter chain
    // (which READS it on every request) and AuthController (which SAVES it at login)
    // use the same object.
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // Tell Spring Security how to authenticate email/password credentials.
    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsServiceImpl userDetailsService,
            PasswordEncoder passwordEncoder
    ) {

        // Load accounts from PostgreSQL using our custom UserDetailsService.
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        // Verify submitted passwords against the stored BCrypt hashes.
        provider.setPasswordEncoder(passwordEncoder);

        // AuthenticationManager coordinates authentication providers.
        return new ProviderManager(provider);
    }

    // Configure the security rules applied to incoming HTTP requests.
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository
    ) throws Exception {

        http
            // Enable CORS using the configuration defined above.
            .cors(Customizer.withDefaults())

            // CHANGED: spa() is Spring Security's preset for single-page apps like React.
            // It stores the token in a cookie JavaScript can read (XSRF-TOKEN) and
            // accepts that raw value back in the X-XSRF-TOKEN header.
            // (The previous setup only accepted a "masked" token, so a React app
            // copying the cookie value would get 403 Forbidden.)
            .csrf(csrf -> csrf.spa())

            // Use server-side HTTP sessions to remember authenticated users.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // NEW: load the logged-in user from the same repository AuthController saves to.
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository)
            )

            // Use our database-backed authentication manager.
            .authenticationManager(authenticationManager)

            // NEW: when a request is not logged in, answer 401 Unauthorized.
            // Without this, Spring Security would answer 403 Forbidden, which
            // means "logged in but not allowed", a different situation.
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            // NEW: let Spring Security handle POST /api/auth/logout for us.
            // It invalidates the session, clears the login, and deletes the cookie.
            // Like any POST, it still requires the CSRF header.
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                // Reply "204 No Content" instead of redirecting to a login page
                // (redirects make sense for server-rendered sites, not a React API).
                .logoutSuccessHandler(
                    new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)
                )
            )

            // Define which endpoints can be accessed without logging in.
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/csrf",
                    // NEW: Spring Boot forwards errors to /error internally. If it were
                    // protected, a 400/500 for a logged-out user would turn into a
                    // confusing 401 and hide the real error.
                    "/error"
                ).permitAll()

                // Allow browser preflight requests.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Protect all other endpoints by default.
                .anyRequest().authenticated()
            );

        // Build and register the configured security filter chain.
        return http.build();
    }
}