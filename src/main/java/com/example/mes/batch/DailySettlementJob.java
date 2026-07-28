package com.example.mes.batch;

import com.example.mes.batch.service.DailySettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 日結排程進入點。
 *
 * <p>只負責「什麼時候跑、跑哪一天、失敗了怎麼記錄」，實際邏輯在
 * {@link DailySettlementService}。分開的理由是排程很難測——把邏輯留在 Service，
 * 測試可以直接呼叫，不必等時鐘。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailySettlementJob {

    private final DailySettlementService settlementService;

    /**
     * 每日凌晨 2 點結算前一日。
     *
     * <p>選 2 點是因為夜班在 0 點前後交接完畢，此時前一日資料已經齊全。
     */
    @Scheduled(cron = "${app.batch.daily-settlement-cron:0 0 2 * * *}")
    public void run() {
        LocalDate target = LocalDate.now().minusDays(1);
        try {
            var result = settlementService.settle(target);
            log.info("排程日結成功 {}", result);
        } catch (Exception ex) {
            // 排程例外若不攔截會被 Spring 吞掉，隔天才發現沒跑。
            // 這裡記成 ERROR，正式環境由 log 監控告警；批次可重跑，補跑不會產生重複資料。
            log.error("排程日結失敗，日期={}，可由 API 手動補跑", target, ex);
        }
    }
}
