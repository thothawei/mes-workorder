package com.example.mes.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 業務規則違反。
 *
 * <p>與系統錯誤區分開來：這類例外是「使用者做了不被允許的事」，
 * 現場人員需要看懂錯誤訊息並自行修正，所以訊息一律寫成中文可讀句子。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
