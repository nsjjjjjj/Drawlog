package com.drawlog.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "그룹 멤버만 접근할 수 있습니다."),
    GROUP_FULL(HttpStatus.BAD_REQUEST, "그룹 최대 인원을 초과했습니다."),
    OWNER_TRANSFER_REQUIRED(HttpStatus.BAD_REQUEST, "방장을 먼저 위임해야 합니다."),
    TOPIC_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 해당 날짜에 주제를 제안했습니다."),
    TOPIC_LOCKED_BY_VOTE(HttpStatus.BAD_REQUEST, "투표를 받은 주제는 수정하거나 삭제할 수 없습니다."),
    DRAWING_LOCKED(HttpStatus.BAD_REQUEST, "잠긴 그림은 수정할 수 없습니다."),
    DRAWING_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 그림을 제출했습니다."),
    DRAWING_NOT_FOUND(HttpStatus.NOT_FOUND, "그림을 찾을 수 없습니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅 메시지를 찾을 수 없습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "요청이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
