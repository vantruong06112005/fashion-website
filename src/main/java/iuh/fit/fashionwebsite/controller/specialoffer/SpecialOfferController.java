package iuh.fit.fashionwebsite.controller.specialoffer;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.service.offer.SpecialOfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/special-offers")
@RequiredArgsConstructor
@Slf4j
public class SpecialOfferController {

    private final SpecialOfferService specialOfferService;

    // Lấy danh sách tất cả ưu đãi
    @GetMapping
    public ApiResponse<List<SpecialOffer>> getAll() {
        return ApiResponse.<List<SpecialOffer>>builder()
                .message("Fetched special offers successfully")
                .data(specialOfferService.getAll())
                .build();
    }

    // Lấy chi tiết 1 ưu đãi theo ID
    @GetMapping("/{id}")
    public ApiResponse<SpecialOffer> getById(@PathVariable Long id) {
        return ApiResponse.<SpecialOffer>builder()
                .message("Fetched special offer detail successfully")
                .data(specialOfferService.findById(id))
                .build();
    }

    // Tạo ưu đãi mới
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOffer> create(@RequestBody @Valid SpecialOffer specialOffer) {
        return ApiResponse.<SpecialOffer>builder()
                .message("Special offer has been created successfully")
                .data(specialOfferService.create(specialOffer))
                .build();
    }

    // Cập nhật ưu đãi theo ID
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOffer> update(@PathVariable Long id, @RequestBody @Valid SpecialOffer specialOffer) {
        return ApiResponse.<SpecialOffer>builder()
                .message("Special offer has been updated successfully")
                .data(specialOfferService.update(id, specialOffer))
                .build();
    }

//    // Xóa ưu đãi theo ID
//    @DeleteMapping("/{id}")
//    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
//    public ApiResponse<Void> delete(@PathVariable Long id) {
//        specialOfferService.delete(id);
//        return ApiResponse.<Void>builder()
//                .message("Special offer has been deleted successfully")
//                .build();
//    }
}