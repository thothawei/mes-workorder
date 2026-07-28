package com.example.mes.workorder.web;

import com.example.mes.common.response.ApiResponse;
import com.example.mes.common.response.PageResponse;
import com.example.mes.workorder.domain.WorkOrderStatus;
import com.example.mes.workorder.service.WorkOrderService;
import com.example.mes.workorder.web.WorkOrderDtos.CreateRequest;
import com.example.mes.workorder.web.WorkOrderDtos.ReasonRequest;
import com.example.mes.workorder.web.WorkOrderDtos.WorkOrderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 工單 API。
 *
 * <p>Controller 只做三件事：接參數、呼叫 Service、轉 DTO。
 * 沒有任何商業判斷——那些全部在 Service 與 Entity 裡。
 */
@Tag(name = "工單")
@RestController
@RequestMapping("/api/v1/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @Operation(summary = "建立工單")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkOrderView> create(@Valid @RequestBody CreateRequest req) {
        return ApiResponse.ok(WorkOrderView.from(workOrderService.create(
                req.orderNo(), req.productCode(), req.productName(), req.plannedQty(),
                req.lineCode(), req.plannedStart(), req.plannedEnd())));
    }

    @Operation(summary = "查詢工單")
    @GetMapping("/{orderNo}")
    public ApiResponse<WorkOrderView> get(@PathVariable String orderNo) {
        return ApiResponse.ok(WorkOrderView.from(workOrderService.getByOrderNo(orderNo)));
    }

    @Operation(summary = "工單清單（可依產線與狀態篩選）")
    @GetMapping
    public ApiResponse<PageResponse<WorkOrderView>> search(
            @RequestParam(required = false) String lineCode,
            @RequestParam(required = false) WorkOrderStatus status,
            @PageableDefault(size = 20, sort = "plannedStart") Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                workOrderService.search(lineCode, status, pageable), WorkOrderView::from));
    }

    @Operation(summary = "派工")
    @PostMapping("/{orderNo}/release")
    public ApiResponse<WorkOrderView> release(@PathVariable String orderNo) {
        return ApiResponse.ok(WorkOrderView.from(workOrderService.release(orderNo)));
    }

    @Operation(summary = "暫停")
    @PostMapping("/{orderNo}/hold")
    public ApiResponse<WorkOrderView> hold(@PathVariable String orderNo, @Valid @RequestBody ReasonRequest req) {
        return ApiResponse.ok(WorkOrderView.from(workOrderService.hold(orderNo, req.reason())));
    }

    @Operation(summary = "復工")
    @PostMapping("/{orderNo}/resume")
    public ApiResponse<WorkOrderView> resume(@PathVariable String orderNo) {
        return ApiResponse.ok(WorkOrderView.from(workOrderService.resume(orderNo)));
    }

    @Operation(summary = "退回待派")
    @PostMapping("/{orderNo}/return-to-released")
    public ApiResponse<WorkOrderView> returnToReleased(@PathVariable String orderNo) {
        return ApiResponse.ok(WorkOrderView.from(workOrderService.returnToReleased(orderNo)));
    }

    @Operation(summary = "完工")
    @PostMapping("/{orderNo}/complete")
    public ApiResponse<WorkOrderView> complete(@PathVariable String orderNo) {
        return ApiResponse.ok(WorkOrderView.from(workOrderService.complete(orderNo)));
    }

    @Operation(summary = "取消（限廠務主管）")
    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<WorkOrderView> cancel(@PathVariable String orderNo, @Valid @RequestBody ReasonRequest req) {
        return ApiResponse.ok(WorkOrderView.from(workOrderService.cancel(orderNo, req.reason())));
    }
}
