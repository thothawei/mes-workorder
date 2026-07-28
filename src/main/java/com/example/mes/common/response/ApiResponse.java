package com.example.mes.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 統一 API 回應格式。
 *
 * @param success 是否成功
 * @param data    成功時的資料
 * @param code    失敗時的錯誤代碼，供前端做分支處理
 * @param message 失敗時給人看的訊息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String code,
        String message,
        LocalDateTime timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, code, message, LocalDateTime.now());
    }
}
