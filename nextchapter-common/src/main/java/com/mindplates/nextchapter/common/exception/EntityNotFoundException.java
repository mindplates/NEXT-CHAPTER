package com.mindplates.nextchapter.common.exception;

public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException(String entity, Object id) {
        super(entity + "을(를) 찾을 수 없습니다. ID: " + id);
    }
}
