package com.example.mes.security.user;

/**
 * 角色。
 *
 * <p>對應現場的三個層級。Spring Security 的 hasRole() 會自動補上 ROLE_ 前綴，
 * 所以資料庫存的是不帶前綴的名稱。
 */
public enum Role {

    /** 現場作業員：查工單、報工 */
    OPERATOR,

    /** 線長：建立工單、派工、暫停、完工 */
    LEADER,

    /** 廠務主管：取消工單、全廠報表、手動觸發日結 */
    MANAGER
}
