package com.deefacto.ai_service.advice;

import com.deefacto.ai_service.common.dto.ApiResponseDto;
import com.deefacto.ai_service.common.exception.CustomException;
import com.deefacto.ai_service.common.exception.ErrorCodeInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(value = 1)
@RestControllerAdvice
public class ApiCommonAdvice {
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponseDto<String>> handleCustomException2(CustomException e) {
        ErrorCodeInterface errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus()) // HTTP status
                .body(ApiResponseDto.createError(
                        errorCode.getCode(),       // "COMMON404" 같은 에러코드
                        e.getMessage()     // 메시지
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<String>> handleException(Exception e) {
        log.error("Unhandled exception: ", e);
        return ResponseEntity
                .status(500)
                .body(ApiResponseDto.createError("COMMON500", "Internal Server Error"));
    }
}