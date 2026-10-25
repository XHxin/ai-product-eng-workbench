package com.aiworkbench.backend.service;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    public BusinessException(String code, String message, HttpStatus status) {
        super(message); this.code = code; this.status = status;
    }
    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public static BusinessException conflict(String message) {
        return new BusinessException("STATE_CONFLICT", message, HttpStatus.CONFLICT);
    }
    public static BusinessException missing(String resource) {
        return new BusinessException("NOT_FOUND", resource + "不存在", HttpStatus.NOT_FOUND);
    }
}
