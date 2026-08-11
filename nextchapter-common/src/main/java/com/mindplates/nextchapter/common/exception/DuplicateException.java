package com.mindplates.nextchapter.common.exception;

public class DuplicateException extends BusinessException {

    public DuplicateException(String entity, String field, Object value) {
        super("이미 존재하는 " + entity + "입니다. " + field + ": " + value);
    }

    public DuplicateException(String message) {
        super(message);
    }
}
