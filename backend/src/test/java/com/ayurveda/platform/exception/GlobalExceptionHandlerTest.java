package com.ayurveda.platform.exception;

import com.ayurveda.platform.dto.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Each handler method is invoked directly to verify that it maps the thrown
 * exception to the correct HTTP status code and produces a consistent
 * {@link ApiError} body (status, error reason phrase, message, path, timestamp,
 * and field errors where applicable).
 *
 * <p>Tests Requirements:
 * <ul>
 *   <li>25.1 - Insufficient stock returns HTTP 409 Conflict</li>
 *   <li>25.2 - Error message includes product name, available stock, required quantity</li>
 *   <li>25.3 / 25.4 - Consistent error responses across exception types</li>
 *   <li>31.1-31.5 - Validation failures surface field-level errors with HTTP 400</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final String REQUEST_PATH = "/api/v1/orders";

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        lenient().when(request.getRequestURI()).thenReturn(REQUEST_PATH);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    /**
     * Asserts the fields every error response must populate consistently.
     */
    private void assertConsistentBody(ResponseEntity<ApiError> response,
                                      HttpStatus expectedStatus,
                                      String expectedMessage) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);

        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(body.getError()).isEqualTo(expectedStatus.getReasonPhrase());
        assertThat(body.getMessage()).isEqualTo(expectedMessage);
        assertThat(body.getPath()).isEqualTo(REQUEST_PATH);
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("ResourceNotFoundException maps to HTTP 404 with consistent body")
    void handleResourceNotFound_returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Order", "id", 99L);

        ResponseEntity<ApiError> response = handler.handleResourceNotFound(ex, request);

        assertConsistentBody(response, HttpStatus.NOT_FOUND, ex.getMessage());
        assertThat(response.getBody().getFieldErrors()).isNull();
    }

    @Test
    @DisplayName("TenantNotFoundException maps to HTTP 404 with consistent body")
    void handleTenantNotFound_returns404() {
        TenantNotFoundException ex = new TenantNotFoundException("Tenant not found with key: acme");

        ResponseEntity<ApiError> response = handler.handleTenantNotFound(ex, request);

        assertConsistentBody(response, HttpStatus.NOT_FOUND, "Tenant not found with key: acme");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException maps to HTTP 400 with field errors")
    void handleValidationErrors_returns400WithFieldErrors() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "createOrderRequest");
        bindingResult.addError(new FieldError(
                "createOrderRequest", "customerName", "", false, null, null, "must not be blank"));
        bindingResult.addError(new FieldError(
                "createOrderRequest", "quantity", -1, false, null, null, "must be greater than 0"));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiError> response = handler.handleValidationErrors(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getError()).isEqualTo("Validation Failed");
        assertThat(body.getMessage()).isEqualTo("One or more fields have validation errors");
        assertThat(body.getPath()).isEqualTo(REQUEST_PATH);
        assertThat(body.getTimestamp()).isNotNull();
        assertThat(body.getFieldErrors()).hasSize(2);
        assertThat(body.getFieldErrors())
                .extracting(ApiError.FieldError::getField)
                .containsExactlyInAnyOrder("customerName", "quantity");
        assertThat(body.getFieldErrors())
                .extracting(ApiError.FieldError::getMessage)
                .containsExactlyInAnyOrder("must not be blank", "must be greater than 0");
    }

    @Test
    @DisplayName("ConstraintViolationException maps to HTTP 400 with field errors")
    void handleConstraintViolation_returns400WithFieldErrors() {
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("createOrder.quantity");

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be greater than 0");
        when(violation.getInvalidValue()).thenReturn(-5);

        ConstraintViolationException ex =
                new ConstraintViolationException(Set.<ConstraintViolation<?>>of(violation));

        ResponseEntity<ApiError> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getError()).isEqualTo("Validation Failed");
        assertThat(body.getMessage()).isEqualTo("One or more fields have validation errors");
        assertThat(body.getFieldErrors()).hasSize(1);
        ApiError.FieldError fieldError = body.getFieldErrors().get(0);
        // Property path "createOrder.quantity" is trimmed to the last segment.
        assertThat(fieldError.getField()).isEqualTo("quantity");
        assertThat(fieldError.getMessage()).isEqualTo("must be greater than 0");
        assertThat(fieldError.getRejectedValue()).isEqualTo(-5);
    }

    @Test
    @DisplayName("IllegalArgumentException maps to HTTP 400")
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Order IDs list cannot be empty");

        ResponseEntity<ApiError> response = handler.handleIllegalArgument(ex, request);

        assertConsistentBody(response, HttpStatus.BAD_REQUEST, "Order IDs list cannot be empty");
    }

    @Test
    @DisplayName("InvalidStatusTransitionException maps to HTTP 400")
    void handleInvalidStatusTransition_returns400() {
        InvalidStatusTransitionException ex =
                new InvalidStatusTransitionException("DELIVERED", "PENDING", "Cannot revert a delivered order");

        ResponseEntity<ApiError> response = handler.handleInvalidStatusTransition(ex, request);

        assertConsistentBody(response, HttpStatus.BAD_REQUEST, ex.getMessage());
        assertThat(response.getBody().getMessage()).contains("DELIVERED", "PENDING");
    }

    @Test
    @DisplayName("IllegalStateException maps to HTTP 409 Conflict")
    void handleIllegalState_returns409() {
        IllegalStateException ex = new IllegalStateException("Order status must be PAID or later");

        ResponseEntity<ApiError> response = handler.handleIllegalState(ex, request);

        assertConsistentBody(response, HttpStatus.CONFLICT, "Order status must be PAID or later");
    }

    @Test
    @DisplayName("InsufficientStockException maps to HTTP 409 with product/stock details (Req 25.1, 25.2)")
    void handleInsufficientStock_returns409() {
        InsufficientStockException ex =
                new InsufficientStockException("Ashwagandha Powder", "ASH-100", 5, 10);

        ResponseEntity<ApiError> response = handler.handleInsufficientStock(ex, request);

        assertConsistentBody(response, HttpStatus.CONFLICT, ex.getMessage());
        // Requirement 25.2: message includes product name, available stock, and required quantity.
        assertThat(response.getBody().getMessage())
                .contains("Ashwagandha Powder")
                .contains("ASH-100")
                .contains("5")
                .contains("10");
    }

    @Test
    @DisplayName("BadCredentialsException maps to HTTP 401 with generic message")
    void handleBadCredentials_returns401() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ResponseEntity<ApiError> response = handler.handleBadCredentials(ex, request);

        assertConsistentBody(response, HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @Test
    @DisplayName("UsernameNotFoundException maps to HTTP 401 with generic message")
    void handleUsernameNotFound_returns401() {
        UsernameNotFoundException ex = new UsernameNotFoundException("no such user");

        ResponseEntity<ApiError> response = handler.handleUsernameNotFound(ex, request);

        assertConsistentBody(response, HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @Test
    @DisplayName("UnauthorizedAccessException maps to HTTP 401")
    void handleUnauthorized_returns401() {
        UnauthorizedAccessException ex = new UnauthorizedAccessException("Token expired");

        ResponseEntity<ApiError> response = handler.handleUnauthorized(ex, request);

        assertConsistentBody(response, HttpStatus.UNAUTHORIZED, "Token expired");
    }

    @Test
    @DisplayName("AccessDeniedException maps to HTTP 403 Forbidden")
    void handleAccessDenied_returns403() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        ResponseEntity<ApiError> response = handler.handleAccessDenied(ex, request);

        assertConsistentBody(response, HttpStatus.FORBIDDEN, "Access Denied");
    }

    @Test
    @DisplayName("Generic Exception maps to HTTP 500 with a safe message")
    void handleGenericException_returns500() {
        Exception ex = new RuntimeException("Unexpected null pointer somewhere");

        ResponseEntity<ApiError> response = handler.handleGenericException(ex, request);

        assertConsistentBody(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        // The raw exception detail must not leak to clients.
        assertThat(response.getBody().getMessage()).doesNotContain("null pointer");
    }
}
