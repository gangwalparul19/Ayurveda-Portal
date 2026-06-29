package com.ayurveda.platform.config;

import com.ayurveda.platform.security.JwtAuthenticationEntryPoint;
import com.ayurveda.platform.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration.
 *
 * <p>Implements role-based access control (RBAC) per Requirement 19:
 * <ul>
 *   <li>Stateless session (JWT-based authentication)</li>
 *   <li>CORS enabled for the Angular frontend</li>
 *   <li>Public endpoints: auth, swagger, storefront</li>
 *   <li>Coarse-grained, role-based endpoint protection declared here, complemented by
 *       fine-grained {@code @PreAuthorize} annotations on individual controller methods</li>
 *   <li>HTTP 401 for unauthenticated access (entry point) and HTTP 403 for authenticated
 *       users lacking the required role (access denied handler)</li>
 * </ul>
 *
 * <p><b>Role / permission mapping</b> (supported roles: SUPER_ADMIN, TENANT_ADMIN, MANAGER,
 * SALESPERSON, DISPATCHER, ACCOUNTANT). Authorities are stored as {@code ROLE_<roleName>}.
 * <ul>
 *   <li>SUPER_ADMIN  - all operations across all tenants plus tenant management</li>
 *   <li>TENANT_ADMIN - all operations within a tenant plus user management</li>
 *   <li>MANAGER      - view/create/update orders, manage products, generate reports</li>
 *   <li>SALESPERSON  - create orders and update order status; no user management</li>
 *   <li>DISPATCHER   - view PAID/PACKED orders, update dispatch status, generate labels</li>
 *   <li>ACCOUNTANT   - record payments, run reports, generate billing exports</li>
 * </ul>
 *
 * <p>Request-level rules below are intentionally the permissive union of the roles allowed
 * by the underlying controller methods. Method-level {@code @PreAuthorize} annotations narrow
 * access further. Both checks must pass, so the effective authorization is the intersection;
 * any role outside the union receives HTTP 403 Forbidden.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    // Role name constants (without the Spring Security ROLE_ prefix).
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String TENANT_ADMIN = "TENANT_ADMIN";
    private static final String MANAGER = "MANAGER";
    private static final String SALESPERSON = "SALESPERSON";
    private static final String DISPATCHER = "DISPATCHER";
    private static final String ACCOUNTANT = "ACCOUNTANT";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // -------- Public endpoints --------
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/storefront/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Development endpoints (only active under the dev profile)
                        .requestMatchers("/dev/**").permitAll()

                        // -------- Platform administration --------
                        // Tenant management is restricted to the platform Super Admin.
                        .requestMatchers("/admin/tenants/**").hasRole(SUPER_ADMIN)
                        // User management is restricted to admins; SALESPERSON and other
                        // operational roles are explicitly excluded (Req 19.2).
                        .requestMatchers("/admin/users/**").hasAnyRole(SUPER_ADMIN, TENANT_ADMIN)
                        .requestMatchers("/admin/**").hasRole(SUPER_ADMIN)

                        // -------- Order endpoints (Req 19.2) --------
                        // Order creation: admins, managers, salespersons.
                        .requestMatchers(HttpMethod.POST, "/orders/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, SALESPERSON)
                        // Status updates: admins, managers, salespersons, dispatchers.
                        .requestMatchers(HttpMethod.PUT, "/orders/*/status")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, SALESPERSON, DISPATCHER)
                        .requestMatchers(HttpMethod.PATCH, "/orders/*/status")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, SALESPERSON, DISPATCHER)
                        // Returns: admins, managers, accountants.
                        .requestMatchers(HttpMethod.POST, "/orders/*/return")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, ACCOUNTANT)
                        // Bulk status operations: admins, managers, dispatchers.
                        .requestMatchers(HttpMethod.POST, "/orders/bulk/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, DISPATCHER)
                        // Generic order updates: admins, managers, salespersons.
                        .requestMatchers(HttpMethod.PUT, "/orders/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, SALESPERSON)
                        // Order cancellation/deletion: admins, managers.
                        .requestMatchers(HttpMethod.DELETE, "/orders/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER)
                        // Viewing orders: all operational roles.
                        .requestMatchers(HttpMethod.GET, "/orders/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, SALESPERSON, DISPATCHER, ACCOUNTANT)

                        // -------- Product endpoints --------
                        .requestMatchers(HttpMethod.POST, "/products/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER)
                        .requestMatchers(HttpMethod.PUT, "/products/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER)
                        .requestMatchers(HttpMethod.DELETE, "/products/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/products/**").authenticated()

                        // -------- Customer endpoints --------
                        .requestMatchers("/customers/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, SALESPERSON, ACCOUNTANT, DISPATCHER)

                        // -------- Report endpoints (Req 19.4) --------
                        .requestMatchers("/reports/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, ACCOUNTANT)

                        // -------- Dispatch endpoints (Req 19.3) --------
                        // Dispatchers view PAID/PACKED orders and generate labels.
                        .requestMatchers("/dispatch/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, DISPATCHER, SALESPERSON)

                        // -------- Billing endpoints (Req 19.4) --------
                        // Payment/billing exports for admins, managers, accountants.
                        .requestMatchers("/billing/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, ACCOUNTANT)

                        // -------- Salesperson management --------
                        .requestMatchers("/salesperson/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER, SALESPERSON, ACCOUNTANT)

                        // -------- Review management (admin) --------
                        // PATCH approve and DELETE require TENANT_ADMIN or MANAGER (enforced via
                        // @PreAuthorize on the controller methods). GET pending also guarded there.
                        // The request-matcher just ensures authenticated users can reach the endpoint.
                        .requestMatchers("/reviews/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER)

                        // -------- Coupon management (admin) --------
                        .requestMatchers("/coupons/**")
                        .hasAnyRole(SUPER_ADMIN, TENANT_ADMIN, MANAGER)

                        // All other endpoints require authentication.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Role hierarchy expressing inherited authority. A higher role automatically holds the
     * authorities of every role reachable below it in the DAG, so SUPER_ADMIN gains the full
     * access of TENANT_ADMIN (and transitively MANAGER, ACCOUNTANT, DISPATCHER, SALESPERSON),
     * while TENANT_ADMIN gains MANAGER/ACCOUNTANT/DISPATCHER/SALESPERSON. Lower roles inherit
     * nothing upward, so a SALESPERSON is still denied where only higher roles are permitted.
     *
     * <p>This single declaration replaces the need to list SUPER_ADMIN on every method-level
     * {@code @PreAuthorize} and request matcher. Authorities use the {@code ROLE_} prefix as
     * required by {@link RoleHierarchyImpl}.
     *
     * <p>Declared {@code static} so it is instantiated early, before method-security
     * infrastructure that depends on it, avoiding bean-initialization ordering issues (the
     * documented Spring Security pattern).
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPER_ADMIN > ROLE_TENANT_ADMIN
                ROLE_TENANT_ADMIN > ROLE_MANAGER
                ROLE_TENANT_ADMIN > ROLE_ACCOUNTANT
                ROLE_TENANT_ADMIN > ROLE_DISPATCHER
                ROLE_MANAGER > ROLE_SALESPERSON
                """);
    }

    /**
     * Wires the {@link RoleHierarchy} into method security ({@code @PreAuthorize}/{@code @PostAuthorize}),
     * so {@code hasRole}/{@code hasAnyRole} expressions on controller methods honour inherited
     * roles. Declared {@code static} per the Spring Security recommendation for method-security
     * beans to avoid premature initialization of the surrounding configuration.
     *
     * <p>Web (request-matcher) authorization picks up the {@code RoleHierarchy} bean
     * automatically in Spring Security 6.3, so no additional wiring is needed there.
     */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
