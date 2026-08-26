package com.foreigninone.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    PAYCHECK_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYCHECK_NOT_FOUND", "급여 분석 정보를 찾을 수 없습니다."),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "문서를 찾을 수 없습니다."),
    BANK_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK_TRANSACTION_NOT_FOUND", "금융 거래 내역을 찾을 수 없습니다."),
    CALENDAR_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CALENDAR_EVENT_NOT_FOUND", "캘린더 일정을 찾을 수 없습니다."),
    FILE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_UPLOAD_ERROR", "파일 업로드 중 오류가 발생했습니다."),
    OCR_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "OCR_PROCESSING_ERROR", "OCR 처리 중 오류가 발생했습니다."),
    AI_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI_PROCESSING_ERROR", "AI 분석 중 오류가 발생했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
