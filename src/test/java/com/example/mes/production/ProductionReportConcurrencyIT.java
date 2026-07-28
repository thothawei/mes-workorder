package com.example.mes.production;

import com.example.mes.material.domain.Inventory;
import com.example.mes.material.repository.InventoryRepository;
import com.example.mes.material.repository.MaterialTransactionRepository;
import com.example.mes.production.repository.ProductionReportRepository;
import com.example.mes.production.service.ProductionReportFacade;
import com.example.mes.production.service.SubmitReportCommand;
import com.example.mes.support.AbstractIntegrationTest;
import com.example.mes.workorder.domain.WorkOrder;
import com.example.mes.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 併發報工測試——**這個專案的招牌測試**。
 *
 * <p>單執行緒的測試證明不了任何併發承諾。這裡起多條執行緒同時打同一張工單，
 * 驗證兩件在現場真的會發生的事：
 * <ol>
 *   <li>作業員連按送出（網路慢、平板沒回應）→ 系統只能認一次，庫存只能扣一次</li>
 *   <li>輪班交接時兩人同時報工 → 產出必須精確累加，不能少算也不能多算</li>
 * </ol>
 *
 * <p>任何偷懶的實作——少了 UNIQUE 約束、少了鎖、把重試寫在交易內——都會在這裡露餡。
 */
@DisplayName("併發報工")
class ProductionReportConcurrencyIT extends AbstractIntegrationTest {

    private static final String WAREHOUSE = "WH-RAW";
    private static final String SEAL = "M-SEAL-01";
    private static final int THREADS = 10;

    @Autowired ProductionReportFacade reportFacade;
    @Autowired WorkOrderRepository workOrderRepository;
    @Autowired ProductionReportRepository reportRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired MaterialTransactionRepository transactionRepository;
    @Autowired JdbcTemplate jdbc;

    private String orderNo;

    @BeforeEach
    void setUp() {
        resetTransactionalData(jdbc);
        // 併發測試要跑滿 10 條執行緒，油封庫存加大以免測到庫存不足而不是併發行為
        jdbc.update("UPDATE inventory SET qty_on_hand = 1000, version = version + 1 WHERE material_code = ?", SEAL);

        orderNo = "WO-CC-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();
        WorkOrder wo = WorkOrder.create(orderNo, "P-RAIL-100", "線性滑軌滑座 100mm",
                100, "LINE-A", now, now.plusHours(8));
        wo.release();
        workOrderRepository.saveAndFlush(wo);
    }

    private BigDecimal stockOf(String materialCode) {
        return inventoryRepository.findByMaterialCodeAndWarehouseCode(materialCode, WAREHOUSE)
                .map(Inventory::getQtyOnHand)
                .orElseThrow();
    }

    /**
     * 讓所有執行緒在同一瞬間開跑。
     *
     * <p>用 CountDownLatch 當發令槍，而不是直接 submit 後就結束——
     * 沒有這個閘門，執行緒會被 pool 逐一啟動，實際上根本沒有併發，測試會假通過。
     */
    private List<Throwable> runConcurrently(int threads, java.util.function.IntConsumer task)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int index = i;
            pool.submit(() -> {
                // SecurityContext 是 ThreadLocal，每條執行緒都要自己登入
                loginAs("operator01", "OPERATOR");
                try {
                    startGate.await();
                    task.accept(index);
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    logout();
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(finished).as("併發任務未在時限內完成，可能發生死結").isTrue();
        return failures;
    }

    @Test
    @DisplayName("10 條執行緒送出同一個 idempotencyKey：只產生 1 筆報工，庫存只扣 1 次")
    void sameIdempotencyKeyConsumesOnlyOnce() throws InterruptedException {
        String sharedKey = "same-key-" + UUID.randomUUID();
        BigDecimal before = stockOf(SEAL);
        AtomicInteger accepted = new AtomicInteger();

        List<Throwable> failures = runConcurrently(THREADS, i -> {
            reportFacade.submit(new SubmitReportCommand(
                    orderNo, WAREHOUSE, "MC-01", 5, 0, null, 30, sharedKey), "operator01");
            accepted.incrementAndGet();
        });

        assertThat(failures).as("重送應被安靜地吸收，不該有任何執行緒收到例外").isEmpty();
        assertThat(accepted.get()).isEqualTo(THREADS);

        assertThat(reportRepository.count()).as("只能有一筆報工紀錄").isEqualTo(1);

        WorkOrder wo = workOrderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(wo.getProducedQty()).as("產出只能累加一次").isEqualTo(5);

        // 5 件 × 2 SET = 10 SET
        assertThat(stockOf(SEAL)).as("庫存只能扣一次").isEqualByComparingTo(before.subtract(new BigDecimal("10")));
        assertThat(transactionRepository.findByWorkOrderIdOrderByIdAsc(wo.getId()))
                .as("物料流水也只能有一組").hasSize(3);
    }

    @Test
    @DisplayName("10 條執行緒各報 1 件（不同 key）：產出精確累加為 10，不多不少")
    void distinctReportsAccumulateExactly() throws InterruptedException {
        BigDecimal before = stockOf(SEAL);

        List<Throwable> failures = runConcurrently(THREADS, i ->
                reportFacade.submit(new SubmitReportCommand(
                        orderNo, WAREHOUSE, "MC-01", 1, 0, null, 10, "distinct-" + i), "operator01"));

        assertThat(failures).as("樂觀鎖衝突應由 Facade 重試吸收").isEmpty();

        WorkOrder wo = workOrderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(wo.getProducedQty())
                .as("少算代表更新遺失（lost update），多算代表重複計入")
                .isEqualTo(THREADS);

        assertThat(reportRepository.count()).isEqualTo(THREADS);
        // 10 件 × 2 SET = 20 SET
        assertThat(stockOf(SEAL)).isEqualByComparingTo(before.subtract(new BigDecimal("20")));
    }

    @Test
    @DisplayName("併發下庫存餘額必須等於期初減去所有流水——對不上就是有交易漏掉了")
    void inventoryReconcilesWithTransactions() throws InterruptedException {
        BigDecimal before = stockOf(SEAL);

        runConcurrently(THREADS, i ->
                reportFacade.submit(new SubmitReportCommand(
                        orderNo, WAREHOUSE, "MC-01", 2, 0, null, 10, "recon-" + i), "operator01"));

        BigDecimal totalConsumed = jdbc.queryForObject(
                "SELECT COALESCE(SUM(qty), 0) FROM material_transaction WHERE material_code = ?",
                BigDecimal.class, SEAL);

        // 流水以負數儲存，所以是相加
        assertThat(stockOf(SEAL)).isEqualByComparingTo(before.add(totalConsumed));
    }
}
