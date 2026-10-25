package iuh.fit.fashionwebsite.dto.request;

import iuh.fit.fashionwebsite.enums.ReturnRequestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestCreationRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "Request type is required (RETURN, EXCHANGE)")
    private ReturnRequestType requestType;

    @NotBlank(message = "Reason cannot be blank")
    @Size(max = 100, message = "Reason must be less than 100 characters")
    private String reason;

    @Size(max = 1000, message = "Reason detail must be less than 1000 characters")
    private String reasonDetail;

    @NotEmpty(message = "Return request must contain at least one item")
    @Valid
    private List<ReturnRequestItemRequest> items;

    private List<String> imageUrls;
}
