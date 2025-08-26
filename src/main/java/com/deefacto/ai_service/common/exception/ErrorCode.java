package com.deefacto.ai_service.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode implements ErrorCodeInterface{
    // 400 Bad Request
    INVALID_INPUT("INVALID_INPUT_400", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_FIELD("MISSING_REQUIRED_FIELD_400", "필수 항목이 누락되었습니다.", HttpStatus.BAD_REQUEST),
    BAD_PARAMETER("BAD_PARAMETER_400", "파라미터 값이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_FORMAT("INVALID_FORMAT_400", "데이터 형식이 잘못되었습니다.", HttpStatus.BAD_REQUEST),
    DUPLICATE_REQUEST("DUPLICATE_REQUEST_400", "중복된 요청입니다.", HttpStatus.BAD_REQUEST),

    // 401 Unauthorized
    UNAUTHORIZED("UNAUTHORIZED_401", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("INVALID_TOKEN_401", "토큰이 유효하지 않습니다.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("TOKEN_EXPIRED_401", "토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND_IN_TOKEN("USER_NOT_FOUND_IN_TOKEN_401", "토큰에 해당하는 사용자가 존재하지 않습니다.", HttpStatus.UNAUTHORIZED),

    // 403 Forbidden
    FORBIDDEN("FORBIDDEN_403", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    ACCESS_DENIED("ACCESS_DENIED_403", "리소스 접근이 거부되었습니다.", HttpStatus.FORBIDDEN),

    // 404 Not Found
    NOT_FOUND("NOT_FOUND_404", "리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND("USER_NOT_FOUND_404", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ITEM_NOT_FOUND("ITEM_NOT_FOUND_404", "대상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // 409 Conflict
    DUPLICATE_RESOURCE("DUPLICATE_RESOURCE_409", "이미 존재하는 리소스입니다.", HttpStatus.CONFLICT),
    CONFLICT_STATE("CONFLICT_STATE_409", "리소스 상태가 충돌합니다.", HttpStatus.CONFLICT),

    // 500 Internal Server Error
    INTERNAL_ERROR("INTERNAL_ERROR_500", "서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR("DATABASE_ERROR_500", "데이터베이스 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    EXTERNAL_SERVICE_ERROR("EXTERNAL_SERVICE_ERROR_500", "외부 서비스 호출 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
