package com.example.mes.workorder.domain;

/**
 * 工單狀態。
 *
 * <p>順序刻意由「早」到「晚」排列，但不可依賴 ordinal 做比較——
 * 合法轉換一律查 {@link WorkOrderTransition}，因為現場流程會改，順序不是規則。
 */
public enum WorkOrderStatus {

    /** 已建立，尚未派到產線 */
    DRAFT("草稿"),

    /** 已派工，等待現場開工 */
    RELEASED("已派工"),

    /** 生產中（首次報工後自動進入） */
    IN_PROGRESS("生產中"),

    /** 暫停：待料、設備異常、換模 */
    ON_HOLD("暫停"),

    /** 完工，等待日結 */
    COMPLETED("已完工"),

    /** 日結後結案，不可再異動 */
    CLOSED("已結案"),

    /** 取消 */
    CANCELLED("已取消");

    private final String label;

    WorkOrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 終態不可再轉出任何狀態 */
    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    /** 是否允許現場報工 */
    public boolean acceptsProduction() {
        return this == RELEASED || this == IN_PROGRESS;
    }
}
