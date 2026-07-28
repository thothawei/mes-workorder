package com.example.mes.production.service;

import com.example.mes.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 報工的交易外層：**併發衝突的處理都在這裡，而不在交易內**。
 *
 * <p>這個類別存在的唯一理由，是 Spring 交易的一個硬性限制：
 * 交易一旦因例外被標記為 rollback-only，在同一個交易裡做任何補救（重試、改查既有資料）
 * 都會連帶失敗。所以重試與冪等回查必須發生在交易邊界**之外**，也就是這一層。
 *
 * <p>順帶一提，它也不能寫成 {@code ProductionReportService} 裡的另一個方法——
 * 同類別內的自我呼叫會繞過 Spring AOP proxy，{@code @Transactional} 根本不會生效。
 * 這是實務上很常踩、但單元測試抓不到的坑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionReportFacade {

    /** 樂觀鎖衝突的重試次數。共用料在尖峰時可能連續衝突，但超過三次代表設計要重新檢討。 */
    private static final int MAX_RETRY = 3;

    private final ProductionReportService reportService;

    public ProductionReportResult submit(SubmitReportCommand cmd, String operator) {
        OptimisticLockingFailureException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return reportService.submit(cmd, operator);

            } catch (DataIntegrityViolationException ex) {
                // 併發重送：另一個執行緒剛寫入同一個 idempotency_key，被 UNIQUE 約束擋下。
                // 本次交易已回滾（沒有重複扣料），改讀對方寫入的結果回給現場。
                log.info("偵測到併發重送 key={}，改回傳既有報工結果", cmd.idempotencyKey());
                return reportService.findByIdempotencyKey(cmd.idempotencyKey())
                        .orElseThrow(() -> new BusinessException(
                                "REPORT_CONFLICT",
                                "報工資料衝突，請重新送出",
                                HttpStatus.CONFLICT));

            } catch (OptimisticLockingFailureException ex) {
                // 庫存被其他工單同時異動。整筆已回滾，重跑一次完整流程是安全的：
                // idempotencyKey 相同，不會產生第二筆報工紀錄。
                lastFailure = ex;
                log.warn("庫存樂觀鎖衝突，第 {}/{} 次重試 工單={}", attempt, MAX_RETRY, cmd.orderNo());
            }
        }

        throw lastFailure;
    }
}
