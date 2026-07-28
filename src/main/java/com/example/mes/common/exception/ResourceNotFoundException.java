package com.example.mes.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 查無資料。
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object key) {
        super("NOT_FOUND", "找不到 %s：%s".formatted(resource, key), HttpStatus.NOT_FOUND);
    }
}
