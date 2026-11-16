
package com.smashit_beta.backend.config;

import com.smashit_beta.backend.security.UserDetailsServiceImpl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

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
            AuthenticationManager authenticationManager
    ) throws Exception {

        http
            // Enable CORS using the configuration defined above.
            .cors(Customizer.withDefaults())

            // Keep CSRF protection enabled for browser-based session auth.
            .csrf(csrf -> csrf
                .csrfTokenRepository(
                    CookieCsrfTokenRepository.withHttpOnlyFalse()
                )
            )

            // Use server-side HTTP sessions to remember authenticated users.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // Use our database-backed authentication manager.
            .authenticationManager(authenticationManager)

            // Define which endpoints can be accessed without logging in.
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/csrf"
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
