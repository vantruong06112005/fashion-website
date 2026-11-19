package iuh.fit.fashionwebsite.controller.product;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ProductStatisticsResponse;
import iuh.fit.fashionwebsite.dto.response.StockAlertPageResponse;
import iuh.fit.fashionwebsite.service.product.ProductStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/admin/dashboard/products")
@RequiredArgsConstructor
@Validated
public class ProductStatisticsController {
    private final ProductStatisticsService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<ProductStatisticsResponse> getStatistics(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(defaultValue = "5") @Min(1) @Max(100) int topLimit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.<ProductStatisticsResponse>builder().code(200).data(service.getStatistics(days, topLimit, from, to)).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalidPeriod(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.<Void>builder().code(400).message(exception.getMessage()).build());
    }

    @GetMapping("/stock-alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<StockAlertPageResponse> getStockAlerts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int pageSize) {
        return ApiResponse.<StockAlertPageResponse>builder().code(200).data(service.getStockAlerts(page, pageSize)).build();
    }
}
