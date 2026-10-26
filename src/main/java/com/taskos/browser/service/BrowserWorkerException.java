package com.taskos.browser.service;

public class BrowserWorkerException extends RuntimeException {
    private final String code;

    public BrowserWorkerException(String message, String code) {
        super(message);
        this.code = code;
    }

    public BrowserWorkerException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() { return code; }
}
