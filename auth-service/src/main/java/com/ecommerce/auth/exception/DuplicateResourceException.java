package com.ecommerce.auth.exception;

public class DuplicateResourceException extends IllegalArgumentException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}

