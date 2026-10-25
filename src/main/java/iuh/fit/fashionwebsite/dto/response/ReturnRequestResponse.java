package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestResponse {

    private Long id;
    private String returnCode;
    private Long userId;
    private Long orderId;
    private String orderCode;
    private String reason;
    private String reasonDetail;
    private ReturnRequestStatus status;
    private List<ReturnRequestItemResponse> items;
    private List<ReturnRequestImageResponse> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
