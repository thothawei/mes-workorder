package com.example.mes.report.web;

import com.example.mes.common.exception.BusinessException;
import com.example.mes.common.response.ApiResponse;
import com.example.mes.report.dto.ReportProjections.DefectPareto;
import com.example.mes.report.dto.ReportProjections.LineDailyOutput;
import com.example.mes.report.dto.ReportProjections.MaterialUsageRank;
import com.example.mes.report.repository.ReportQueryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Tag(name = "報表")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    /** 兩班制每日可用工時（分鐘），稼動率分母 */
    private static final int AVAILABLE_MINUTES_PER_DAY = 960;

    /** 查詢區間上限。報表掃全表是拖垮正式環境最常見的原因，在入口就擋住。 */
    private static final long MAX_QUERY_DAYS = 92;

    private final ReportQueryRepository reportRepository;

    @Operation(summary = "產線日產出與稼動率")
    @GetMapping("/line-daily-output")
    @PreAuthorize("hasAnyRole('LEADER','MANAGER')")
    public ApiResponse<List<LineDailyOutput>> lineDailyOutput(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateRange(from, to);
        return ApiResponse.ok(reportRepository.lineDailyOutput(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(), AVAILABLE_MINUTES_PER_DAY));
    }

    @Operation(summary = "不良原因柏拉圖")
    @GetMapping("/defect-pareto")
    @PreAuthorize("hasAnyRole('LEADER','MANAGER')")
    public ApiResponse<List<DefectPareto>> defectPareto(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateRange(from, to);
        return ApiResponse.ok(reportRepository.defectPareto(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    @Operation(summary = "物料耗用排行（含低於安全庫存標示）")
    @GetMapping("/material-usage")
    @PreAuthorize("hasAnyRole('LEADER','MANAGER')")
    public ApiResponse<List<MaterialUsageRank>> materialUsage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateRange(from, to);
        return ApiResponse.ok(reportRepository.materialUsageRank(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException("INVALID_DATE_RANGE", "結束日期不可早於開始日期");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_QUERY_DAYS) {
            throw new BusinessException("DATE_RANGE_TOO_WIDE",
                    "查詢區間不可超過 %d 天，請縮小範圍或改用日結報表".formatted(MAX_QUERY_DAYS));
        }
    }
}
