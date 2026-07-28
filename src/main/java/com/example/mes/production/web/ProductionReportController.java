package com.example.mes.production.web;

import com.example.mes.common.response.ApiResponse;
import com.example.mes.production.service.ProductionReportFacade;
import com.example.mes.production.service.ProductionReportResult;
import com.example.mes.production.service.SubmitReportCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "報工")
@RestController
@RequestMapping("/api/v1/production-reports")
@RequiredArgsConstructor
public class ProductionReportController {

    private final ProductionReportFacade reportFacade;

    public record SubmitRequest(
            @NotBlank @Size(max = 30) String orderNo,
            @NotBlank @Size(max = 20) String warehouseCode,
            @Size(max = 20) String machineCode,
            @Min(0) int goodQty,
            @Min(0) int defectQty,
            @Size(max = 50) String defectReason,
            @Min(0) int workMinutes,
            @NotBlank @Size(max = 64) String idempotencyKey
    ) {
    }

    /**
     * 提交報工。
     *
     * <p>回應碼刻意分兩種：首次受理回 201，重送回 200。
     * 現場平板可以據此顯示「報工成功」或「此筆已受理，未重複計算」，
     * 而不是兩種情況都跳一樣的訊息讓作業員疑惑。
     */
    @Operation(summary = "提交報工（具冪等性，重送不會重複扣料）")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductionReportResult>> submit(
            @Valid @RequestBody SubmitRequest req,
            @AuthenticationPrincipal String operator) {

        ProductionReportResult result = reportFacade.submit(new SubmitReportCommand(
                req.orderNo(), req.warehouseCode(), req.machineCode(),
                req.goodQty(), req.defectQty(), req.defectReason(),
                req.workMinutes(), req.idempotencyKey()), operator);

        HttpStatus status = result.duplicated() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(result));
    }
}
