package com.ayurveda.platform.controller;

import com.ayurveda.platform.security.JwtTokenProvider;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.StorefrontUser;
import com.ayurveda.platform.tenant.repository.CustomerRepository;
import com.ayurveda.platform.tenant.repository.StorefrontUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Storefront authentication endpoints (register / login / profile).
 * All paths are under /storefront/** which is already permitAll() in SecurityConfig,
 * so token extraction is done manually rather than relying on Spring Security principal.
 */
@RestController
@RequestMapping("/storefront/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
@Profile("simple")
public class StorefrontAuthController {

    private final StorefrontUserRepository storefrontUserRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    // -------------------------------------------------------------------------
    // POST /storefront/auth/register
    // -------------------------------------------------------------------------

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String email    = body.get("email");
        String password = body.get("password");
        String fullName = body.get("fullName");
        String phone    = body.get("phone");
        String address  = body.get("address");
        String city     = body.get("city");
        String state    = body.get("state");
        String pincode  = body.get("pincode");

        if (!StringUtils.hasText(email) || !StringUtils.hasText(password) || !StringUtils.hasText(fullName)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "email, password, and fullName are required"));
        }

        if (storefrontUserRepository.existsByEmail(email.toLowerCase())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", "Email is already registered"));
        }

        // Find or create the Customer record
        Customer customer = customerRepository.findByPhone(phone != null ? phone : "")
                .orElseGet(() -> {
                    Customer c = new Customer();
                    c.setName(fullName);
                    c.setEmail(email.toLowerCase());
                    c.setPhone(phone);
                    c.setAddressLine1(address);
                    c.setCity(city);
                    c.setState(state);
                    c.setPincode(pincode);
                    return customerRepository.save(c);
                });

        StorefrontUser user = StorefrontUser.builder()
                .email(email.toLowerCase())
                .phone(phone)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(password))
                .customerId(customer.getId())
                .isVerified(false)
                .isActive(true)
                .build();

        storefrontUserRepository.save(user);
        log.info("Storefront user registered: {}", email);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "message", "Registration successful"));
    }

    // -------------------------------------------------------------------------
    // POST /storefront/auth/login
    // -------------------------------------------------------------------------

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String email    = body.get("email");
        String password = body.get("password");

        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "email and password are required"));
        }

        StorefrontUser user = storefrontUserRepository.findByEmail(email.toLowerCase())
                .orElse(null);

        if (user == null || !Boolean.TRUE.equals(user.getIsActive())
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Invalid email or password"));
        }

        // Update last login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        storefrontUserRepository.save(user);

        String token = jwtTokenProvider.generateAccessToken(
                user.getEmail(), null, null,
                user.getId(), user.getFullName(), "STOREFRONT_USER");

        log.info("Storefront user logged in: {}", email);

        return ResponseEntity.ok(Map.of(
                "accessToken", token,
                "userId",      user.getId(),
                "email",       user.getEmail(),
                "fullName",    user.getFullName(),
                "customerId",  user.getCustomerId() != null ? user.getCustomerId() : ""
        ));
    }

    // -------------------------------------------------------------------------
    // GET /storefront/auth/profile
    // -------------------------------------------------------------------------

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(HttpServletRequest request) {
        StorefrontUser user = resolveUserFromRequest(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Authentication required"));
        }

        Customer customer = user.getCustomerId() != null
                ? customerRepository.findById(user.getCustomerId()).orElse(null)
                : null;

        return ResponseEntity.ok(buildProfileResponse(user, customer));
    }

    // -------------------------------------------------------------------------
    // PUT /storefront/auth/profile
    // -------------------------------------------------------------------------

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {

        StorefrontUser user = resolveUserFromRequest(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Authentication required"));
        }

        if (StringUtils.hasText(body.get("fullName"))) {
            user.setFullName(body.get("fullName"));
        }
        if (StringUtils.hasText(body.get("phone"))) {
            user.setPhone(body.get("phone"));
        }
        storefrontUserRepository.save(user);

        // Sync to Customer record
        if (user.getCustomerId() != null) {
            customerRepository.findById(user.getCustomerId()).ifPresent(c -> {
                if (StringUtils.hasText(body.get("fullName")))  c.setName(body.get("fullName"));
                if (StringUtils.hasText(body.get("phone")))     c.setPhone(body.get("phone"));
                if (StringUtils.hasText(body.get("address")))   c.setAddressLine1(body.get("address"));
                if (StringUtils.hasText(body.get("city")))      c.setCity(body.get("city"));
                if (StringUtils.hasText(body.get("state")))     c.setState(body.get("state"));
                if (StringUtils.hasText(body.get("pincode")))   c.setPincode(body.get("pincode"));
                customerRepository.save(c);
            });
        }

        Customer customer = user.getCustomerId() != null
                ? customerRepository.findById(user.getCustomerId()).orElse(null)
                : null;

        return ResponseEntity.ok(buildProfileResponse(user, customer));
    }

    // -------------------------------------------------------------------------
    // PUT /storefront/auth/change-password
    // -------------------------------------------------------------------------

    @PutMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {

        StorefrontUser user = resolveUserFromRequest(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Authentication required"));
        }

        String currentPassword = body.get("currentPassword");
        String newPassword     = body.get("newPassword");

        if (!StringUtils.hasText(currentPassword) || !StringUtils.hasText(newPassword)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "currentPassword and newPassword are required"));
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Current password is incorrect"));
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        storefrontUserRepository.save(user);

        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts and validates the Bearer JWT from the Authorization header,
     * then loads the corresponding StorefrontUser.
     * Returns null if the token is absent, invalid, or the user does not exist.
     */
    private StorefrontUser resolveUserFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return null;
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        if (userId == null) {
            return null;
        }
        return storefrontUserRepository.findById(userId).orElse(null);
    }

    private Map<String, Object> buildProfileResponse(StorefrontUser user, Customer customer) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("userId",     user.getId());
        map.put("email",      user.getEmail());
        map.put("fullName",   user.getFullName());
        map.put("phone",      user.getPhone());
        map.put("customerId", user.getCustomerId());
        map.put("isVerified", user.getIsVerified());
        if (customer != null) {
            map.put("address",  customer.getAddressLine1());
            map.put("city",     customer.getCity());
            map.put("state",    customer.getState());
            map.put("pincode",  customer.getPincode());
        }
        return map;
    }
}
