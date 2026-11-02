package com.ecommerce.order.exception;

/**
 * Thrown when the downstream Product Service dependency is unreachable,
 * times out, or returns a 5xx server error during order validation.
 */
public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException(String message) {
        super(message);
    }

    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

