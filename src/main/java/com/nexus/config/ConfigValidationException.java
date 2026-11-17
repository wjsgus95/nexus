package com.nexus.config;

import java.nio.file.Path;

public class ConfigValidationException extends RuntimeException {
    public ConfigValidationException(String message) {
        super(message);
    }

    public ConfigValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigValidationException(Path filePath, String message) {
        super(filePath != null ? "[" + filePath + "] " + message : message);
    }

    public ConfigValidationException(Path filePath, String message, Throwable cause) {
        super(filePath != null ? "[" + filePath + "] " + message : message, cause);
    }
}
