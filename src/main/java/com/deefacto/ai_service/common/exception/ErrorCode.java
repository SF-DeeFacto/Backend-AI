package com.deefacto.ai_service.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode implements ErrorCodeInterface{
    // 400
    INVALID_INPUT("COMMON400", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_FIELD("COMMON400", "필수 항목이 누락되었습니다.", HttpStatus.BAD_REQUEST),

    // 401
    UNAUTHORIZED("COMMON401", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),

    // 403
    FORBIDDEN("COMMON403", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),

    // 404
    NOT_FOUND("COMMON404", "리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // 500
    INTERNAL_ERROR("COMMON500", "서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}