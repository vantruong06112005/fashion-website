package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialOfferCreationRequest {
    @NotBlank
    private String title;
    private Integer discountPercent;
    private BigDecimal discountAmount;
    @NotBlank
    private String offerType;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    @NotNull
    private LocalDateTime startDate;
    @NotNull
    private LocalDateTime endDate;
    private Boolean isActive;   // dùng Boolean (wrapper) để getter là getIsActive()
}