package com.example.mes.production;

import com.example.mes.common.exception.InsufficientInventoryException;
import com.example.mes.material.domain.Inventory;
import com.example.mes.material.repository.InventoryRepository;
import com.example.mes.material.repository.MaterialTransactionRepository;
import com.example.mes.production.repository.ProductionReportRepository;
import com.example.mes.production.service.ProductionReportFacade;
import com.example.mes.production.service.ProductionReportResult;
import com.example.mes.production.service.SubmitReportCommand;
import com.example.mes.support.AbstractIntegrationTest;
import com.example.mes.workorder.domain.WorkOrder;
import com.example.mes.workorder.domain.WorkOrderStatus;
import com.example.mes.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 報工核心交易的整合測試。
 *
 * <p>驗證的是本專案最重要的承諾：**任何一步失敗，帳面數字都不會歪掉**。
 */
@DisplayName("報工交易")
class ProductionReportIT extends AbstractIntegrationTest {

    private static final String WAREHOUSE = "WH-RAW";
    private static final String SEAL = "M-SEAL-01";
    private static final String STEEL = "M-STEEL-01";

    @Autowired ProductionReportFacade reportFacade;
    @Autowired WorkOrderRepository workOrderRepository;
    @Autowired ProductionReportRepository reportRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired MaterialTransactionRepository transactionRepository;
    @Autowired JdbcTemplate jdbc;

    private String orderNo;

    @BeforeEach
    void setUp() {
        loginAs("operator01", "OPERATOR");

        // 每個測試用自己的工單與乾淨的庫存，測試之間互不影響
        resetTransactionalData(jdbc);

        orderNo = "WO-IT-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();
        WorkOrder wo = WorkOrder.create(orderNo, "P-RAIL-100", "線性滑軌滑座 100mm",
                60, "LINE-A", now, now.plusHours(8));
        wo.release();
        workOrderRepository.saveAndFlush(wo);
    }

    @AfterEach
    void tearDown() {
        logout();
    }

    private SubmitReportCommand cmd(int good, int defect, String key) {
        return new SubmitReportCommand(orderNo, WAREHOUSE, "MC-01", good, defect, "SCRATCH", 60, key);
    }

    private BigDecimal stockOf(String materialCode) {
        return inventoryRepository.findByMaterialCodeAndWarehouseCode(materialCode, WAREHOUSE)
                .map(Inventory::getQtyOnHand)
                .orElseThrow();
    }

    @Test
    @DisplayName("報工成功：工單累計、庫存扣減、流水寫入，三者一致")
    void submitSucceeds() {
        ProductionReportResult result = reportFacade.submit(cmd(10, 2, "key-1"), "operator01");

        assertThat(result.duplicated()).isFalse();
        assertThat(result.accumulatedGoodQty()).isEqualTo(10);

        WorkOrder wo = workOrderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(wo.getProducedQty()).isEqualTo(10);
        assertThat(wo.getDefectQty()).isEqualTo(2);
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);

        // 10 件 × 2 SET/件 × (1 + 0 損耗) = 20 SET，剩 80
        assertThat(stockOf(SEAL)).isEqualByComparingTo("80.0000");
        // 10 件 × 1.2 KG/件 × 1.03 損耗 = 12.36 KG，剩 1987.64
        assertThat(stockOf(STEEL)).isEqualByComparingTo("1987.6400");

        assertThat(transactionRepository.findByWorkOrderIdOrderByIdAsc(wo.getId())).hasSize(3);
    }

    @Test
    @DisplayName("流水的餘額快照要與庫存表對得上——對不上代表有程式繞過了正規扣帳路徑")
    void transactionBalanceMatchesInventory() {
        reportFacade.submit(cmd(10, 0, "key-bal"), "operator01");

        WorkOrder wo = workOrderRepository.findByOrderNo(orderNo).orElseThrow();
        var sealTx = transactionRepository.findByWorkOrderIdOrderByIdAsc(wo.getId()).stream()
                .filter(t -> t.getMaterialCode().equals(SEAL))
                .findFirst().orElseThrow();

        assertThat(sealTx.getBalanceAfter()).isEqualByComparingTo(stockOf(SEAL));
        // 耗用以負數儲存
        assertThat(sealTx.getQty()).isEqualByComparingTo("-20.0000");
    }

    @Test
    @DisplayName("庫存不足時整筆回滾：報工紀錄、工單數量、其他物料庫存全部不變")
    void insufficientInventoryRollsBackEverything() {
        // 先把 SEAL 用到只剩 100（可做 50 件）
        reportFacade.submit(cmd(50, 0, "key-fill"), "operator01");
        assertThat(stockOf(SEAL)).isEqualByComparingTo("0.0000");

        BigDecimal steelBefore = stockOf(STEEL);
        long reportCountBefore = reportRepository.count();

        assertThatThrownBy(() -> reportFacade.submit(cmd(1, 0, "key-fail"), "operator01"))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining(SEAL)
                .hasMessageContaining("庫存不足");

        WorkOrder wo = workOrderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(wo.getProducedQty()).as("工單產出不可因失敗的報工而增加").isEqualTo(50);
        assertThat(reportRepository.count()).as("失敗的報工不可留下紀錄").isEqualTo(reportCountBefore);
        assertThat(stockOf(STEEL)).as("同一筆交易內先扣的鋼材必須一起回滾").isEqualByComparingTo(steelBefore);
        assertThat(reportRepository.findByIdempotencyKey("key-fail")).isEmpty();
    }

    @Test
    @DisplayName("重送相同 idempotencyKey：只認第一筆，庫存不會扣第二次")
    void duplicateSubmissionDoesNotConsumeTwice() {
        String key = "key-dup";

        ProductionReportResult first = reportFacade.submit(cmd(10, 0, key), "operator01");
        BigDecimal stockAfterFirst = stockOf(SEAL);

        ProductionReportResult second = reportFacade.submit(cmd(10, 0, key), "operator01");

        assertThat(first.duplicated()).isFalse();
        assertThat(second.duplicated()).as("第二次必須被識別為重送").isTrue();
        assertThat(second.reportId()).isEqualTo(first.reportId());

        assertThat(stockOf(SEAL)).as("重送不可再扣一次料").isEqualByComparingTo(stockAfterFirst);
        assertThat(workOrderRepository.findByOrderNo(orderNo).orElseThrow().getProducedQty()).isEqualTo(10);
        assertThat(reportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("超產被擋下時不可扣料")
    void overProductionDoesNotConsume() {
        BigDecimal before = stockOf(STEEL);

        assertThatThrownBy(() -> reportFacade.submit(cmd(64, 0, "key-over"), "operator01"))
                .hasMessageContaining("超過容許上限");

        assertThat(stockOf(STEEL)).isEqualByComparingTo(before);
        assertThat(reportRepository.count()).isZero();
    }

    @Test
    @DisplayName("只報不良品不扣料，但工單不良數要累計")
    void defectOnlyReportDoesNotConsume() {
        BigDecimal before = stockOf(SEAL);

        reportFacade.submit(cmd(0, 5, "key-defect"), "operator01");

        assertThat(stockOf(SEAL)).isEqualByComparingTo(before);
        WorkOrder wo = workOrderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(wo.getDefectQty()).isEqualTo(5);
        assertThat(wo.getProducedQty()).isZero();
    }
}
