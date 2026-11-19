package iuh.fit.fashionwebsite.dto.response;

import java.util.List;

public record StockAlertPageResponse(List<ProductStatisticsResponse.StockAlert> content,
                                    long totalElements, long totalPages, int number, int size) {}
