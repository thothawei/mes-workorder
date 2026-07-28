package com.example.mes.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

/**
 * 整合測試基底：跑在**真的 PostgreSQL** 上。
 *
 * <p>不用 H2 假裝的理由很實際：本專案倚賴的行為——{@code SELECT ... FOR UPDATE} 的實際
 * 阻塞語意、UNIQUE 約束在併發下的競態、窗口函數的計算——在不同資料庫上表現不同。
 * 用 H2 測出來的「通過」不能證明正式環境也通過。
 *
 * <p>容器以 static 宣告，整個測試類別共用一個實例，不必每個測試方法重啟資料庫。
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("mes_test")
                    .withUsername("mes")
                    .withPassword("mes")
                    // 測試不需要耐久性，關掉 fsync 讓容器快很多
                    .withCommand("postgres", "-c", "fsync=off", "-c", "full_page_writes=off");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // 測試不跑排程，避免日結在測試中途插進來改資料
        registry.add("app.batch.daily-settlement-cron", () -> "-");
    }

    /**
     * 在**目前執行緒**設定登入身分。
     *
     * <p>併發測試必須在每一條執行緒各自呼叫一次：{@code SecurityContextHolder} 預設是
     * ThreadLocal，主執行緒設定的身分不會自動傳播到執行緒池裡的工作執行緒，
     * 沒設就會拿到 AccessDeniedException 而不是預期中的併發衝突。
     */
    protected static void loginAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    protected static void logout() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 把交易性資料清空、庫存還原成種子資料的狀態。
     *
     * <p>所有整合測試類別共用同一個容器（重啟資料庫太慢），所以**每個測試方法都必須
     * 自己把狀態歸零**，不能倚賴「前面沒人動過」。這個方法踩過一次教訓才補上：
     * 原本各測試只清自己在意的表，單獨跑都過，整套跑就因為別的測試改過庫存而失敗。
     *
     * <p>刪除順序依外鍵相依性由子到父。
     */
    protected static void resetTransactionalData(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM daily_production_summary");
        jdbc.update("DELETE FROM material_transaction");
        jdbc.update("DELETE FROM production_report");
        jdbc.update("DELETE FROM work_order WHERE order_no NOT LIKE 'WO-2026%'");

        // 還原種子工單與庫存，數值與 V2__seed_master_data.sql 一致
        jdbc.update("UPDATE work_order SET produced_qty = 0, defect_qty = 0, status = 'RELEASED', "
                + "actual_start = NULL, actual_end = NULL WHERE order_no = 'WO-20260728-001'");
        jdbc.update("UPDATE work_order SET produced_qty = 0, defect_qty = 0, status = 'DRAFT', "
                + "actual_start = NULL, actual_end = NULL WHERE order_no = 'WO-20260728-002'");
        jdbc.update("UPDATE inventory SET qty_on_hand = 2000, version = version + 1 WHERE material_code = 'M-STEEL-01'");
        jdbc.update("UPDATE inventory SET qty_on_hand = 80000, version = version + 1 WHERE material_code = 'M-BALL-01'");
        jdbc.update("UPDATE inventory SET qty_on_hand = 100, version = version + 1 WHERE material_code = 'M-SEAL-01'");
    }
}
