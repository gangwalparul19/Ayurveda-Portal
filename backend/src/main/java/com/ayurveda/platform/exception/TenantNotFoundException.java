package com.ayurveda.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(String message) {
        super(message);
    }

    public TenantNotFoundException(String tenantKey, boolean isByKey) {
        super("Tenant not found with key: " + tenantKey);
    }
}
