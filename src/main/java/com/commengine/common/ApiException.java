package com.commengine.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Object errors;

    public ApiException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public ApiException(String message, HttpStatus status, Object errors) {
        super(message);
        this.status = status;
        this.errors = errors;
    }

    public HttpStatus getStatus() { return status; }
    public Object getErrors() { return errors; }

    public static ApiException notFound(String message) { return new ApiException(message, HttpStatus.NOT_FOUND); }
    public static ApiException badRequest(String message) { return new ApiException(message, HttpStatus.BAD_REQUEST); }
    public static ApiException forbidden(String message) { return new ApiException(message, HttpStatus.FORBIDDEN); }
    public static ApiException unauthorized(String message) { return new ApiException(message, HttpStatus.UNAUTHORIZED); }
    public static ApiException conflict(String message) { return new ApiException(message, HttpStatus.CONFLICT); }
    public static ApiException unprocessable(String message) { return new ApiException(message, HttpStatus.UNPROCESSABLE_ENTITY); }
    public static ApiException unprocessable(String message, Object errors) { return new ApiException(message, HttpStatus.UNPROCESSABLE_ENTITY, errors); }
}
