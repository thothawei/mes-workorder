package com.example.mes.common.exception;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 庫存不足。
 *
 * <p>獨立成一個型別，因為呼叫端需要知道「缺哪一項、缺多少」才能給現場可行動的訊息，
 * 而不是只丟一句「報工失敗」。
 */
@Getter
public class InsufficientInventoryException extends BusinessException {

    private final String materialCode;
    private final BigDecimal required;
    private final BigDecimal available;

    public InsufficientInventoryException(String materialCode, BigDecimal required, BigDecimal available) {
        super("INSUFFICIENT_INVENTORY",
                "物料 %s 庫存不足：需要 %s，可用 %s".formatted(materialCode, required.toPlainString(), available.toPlainString()));
        this.materialCode = materialCode;
        this.required = required;
        this.available = available;
    }
}
