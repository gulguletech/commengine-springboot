package com.commengine.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mirrors utils/apiResponse.js: { success, message, data } on success, { success, message, errors } on failure. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class ApiResponse {

    private ApiResponse() {}

    public static ResponseEntity<Map<String, Object>> ok(Object data) {
        return ok(data, "Success", HttpStatus.OK);
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data, String message) {
        return ok(data, message, HttpStatus.OK);
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data, String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", message);
        body.put("data", data == null ? Map.of() : data);
        return ResponseEntity.status(status).body(body);
    }

    public static ResponseEntity<Map<String, Object>> fail(String message, HttpStatus status) {
        return fail(message, status, null);
    }

    public static ResponseEntity<Map<String, Object>> fail(String message, HttpStatus status, Object errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("errors", errors);
        return ResponseEntity.status(status).body(body);
    }
}
