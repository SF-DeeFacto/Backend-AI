package com.deefacto.ai_service.common.exception;

public class CustomException extends RuntimeException {
    private final ErrorCodeInterface errorCode;
    private final String customMessage;

    public CustomException(ErrorCodeInterface errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.customMessage = errorCode.getMessage();
    }

    public CustomException(ErrorCodeInterface errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.customMessage = customMessage;
    }

    public ErrorCodeInterface getErrorCode() {
        return errorCode;
    }

    public String getCustomMessage() {
        return customMessage;
    }
}
