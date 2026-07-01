package com.networking.ems.snmp.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.networking.ems.snmp.exception.DeviceNotFoundException;
import com.networking.ems.snmp.exception.SnmpException;
import com.networking.ems.snmp.exception.SnmpTimeoutException;

/**
 * Turns SNMP-layer exceptions into clean HTTP responses instead of stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(DeviceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "device_not_found", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "bad_request", "message", ex.getMessage()));
    }

    @ExceptionHandler(SnmpTimeoutException.class)
    public ResponseEntity<Map<String, String>> handleTimeout(SnmpTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(Map.of("error", "snmp_timeout", "message", ex.getMessage()));
    }

    @ExceptionHandler(SnmpException.class)
    public ResponseEntity<Map<String, String>> handleSnmp(SnmpException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "snmp_error", "message", ex.getMessage()));
    }
}
