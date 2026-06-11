package com.team1.codedock.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C002", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "C003", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C005", "서버 내부 오류가 발생했습니다."),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "만료된 토큰입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "A003", "이미 사용 중인 이메일입니다."),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "A004", "이미 사용 중인 사용자명입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),

    // Workspace
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "W001", "워크스페이스를 찾을 수 없습니다."),
    WORKSPACE_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "W002", "워크스페이스 멤버를 찾을 수 없습니다."),

    // Channel
    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "CH001", "채널을 찾을 수 없습니다."),
    THREAD_NOT_FOUND(HttpStatus.NOT_FOUND, "CH002", "스레드를 찾을 수 없습니다."),

    // Document
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", "문서를 찾을 수 없습니다."),

    // ApiSpec
    API_SPEC_NOT_FOUND(HttpStatus.NOT_FOUND, "AS001", "API 명세를 찾을 수 없습니다."),
    SWAGGER_URL_NOT_REGISTERED(HttpStatus.NOT_FOUND, "AS002", "등록된 Swagger URL이 없습니다."),
    SWAGGER_FETCH_ERROR(HttpStatus.BAD_GATEWAY, "AS003", "Swagger URL에서 데이터를 가져오는 데 실패했습니다."),

    // GitHub
    GITHUB_REPO_NOT_FOUND(HttpStatus.NOT_FOUND, "G001", "GitHub 레포지토리를 찾을 수 없습니다."),
    GITHUB_WEBHOOK_INVALID(HttpStatus.BAD_REQUEST, "G002", "유효하지 않은 Webhook 요청입니다."),
    GITHUB_PR_NOT_FOUND(HttpStatus.NOT_FOUND, "G003", "GitHub PR을 찾을 수 없습니다."),
    GITHUB_ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "G004", "GitHub 이슈를 찾을 수 없습니다."),
    GITHUB_NOT_CONNECTED(HttpStatus.BAD_REQUEST, "G005", "GitHub 계정이 연결되지 않았습니다."),
    GITHUB_API_ERROR(HttpStatus.BAD_GATEWAY, "G006", "GitHub API 호출에 실패했습니다."),

    // ERD
    ERD_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "ERD를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
