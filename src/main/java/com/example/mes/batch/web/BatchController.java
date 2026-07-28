package com.example.mes.batch.web;

import com.example.mes.batch.service.DailySettlementService;
import com.example.mes.batch.service.DailySettlementService.SettlementResult;
import com.example.mes.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "批次")
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
public class BatchController {

    private final DailySettlementService settlementService;

    /**
     * 手動補跑日結。
     *
     * <p>批次沒跑成功時，資訊人員要能自己補，不必等隔天或改排程。
     * 因為 settle() 具冪等性，補跑已結算過的日期是安全的。
     */
    @Operation(summary = "手動觸發日結（可重複執行，結果一致）")
    @PostMapping("/daily-settlement")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<SettlementResult> settle(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(settlementService.settle(date));
    }
}
