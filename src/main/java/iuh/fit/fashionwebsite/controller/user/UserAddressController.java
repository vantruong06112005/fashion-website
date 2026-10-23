package iuh.fit.fashionwebsite.controller.user;

import iuh.fit.fashionwebsite.dto.request.UserAddressCreationRequest;
import iuh.fit.fashionwebsite.dto.request.UserAddressUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.UserAddressResponse;
import iuh.fit.fashionwebsite.service.user.UserAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserAddressController {

    private final UserAddressService userAddressService;

    // Thêm địa chỉ mới cho người dùng đang đăng nhập
    @PostMapping("/addresses")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserAddressResponse> createAddress(
            @RequestBody @Valid UserAddressCreationRequest request
    ) {
        return ApiResponse.<UserAddressResponse>builder()
                .message("Address has been added successfully")
                .data(userAddressService.createAddress(request))
                .build();
    }

    // Lấy danh sách địa chỉ của người dùng đang đăng nhập
    @GetMapping("/addresses")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<List<UserAddressResponse>> getMyAddresses() {
        return ApiResponse.<List<UserAddressResponse>>builder()
                .message("Fetched user addresses successfully")
                .data(userAddressService.getMyAddresses())
                .build();
    }

    // Lấy chi tiết 1 địa chỉ theo ID của người dùng đang đăng nhập
    @GetMapping("/addresses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserAddressResponse> getMyAddressById(@PathVariable Long id) {
        return ApiResponse.<UserAddressResponse>builder()
                .message("Fetched address detail successfully")
                .data(userAddressService.getMyAddressById(id))
                .build();
    }

    // Cập nhật địa chỉ theo ID
    @PutMapping("/addresses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<UserAddressResponse> updateAddress(
            @PathVariable Long id,
            @RequestBody @Valid UserAddressUpdateRequest request
    ) {
        return ApiResponse.<UserAddressResponse>builder()
                .message("Address has been updated successfully")
                .data(userAddressService.updateAddress(id, request))
                .build();
    }

    // Xóa địa chỉ theo ID
    @DeleteMapping("/addresses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> deleteAddress(@PathVariable Long id) {
        userAddressService.deleteAddress(id);
        return ApiResponse.<Void>builder()
                .message("Address has been deleted successfully")
                .build();
    }

    // Đặt địa chỉ làm mặc định
    @PatchMapping("/addresses/{id}/default")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserAddressResponse> setDefaultAddress(@PathVariable Long id) {
        return ApiResponse.<UserAddressResponse>builder()
                .message("Default address updated successfully")
                .data(userAddressService.setDefaultAddress(id))
                .build();
    }

    // ADMIN và STAFF xem danh sách địa chỉ của một user cụ thể
    @GetMapping("/{userId}/addresses")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<UserAddressResponse>> getUserAddressesByAdmin(@PathVariable Long userId) {
        return ApiResponse.<List<UserAddressResponse>>builder()
                .message("Fetched user addresses by admin/staff successfully")
                .data(userAddressService.getUserAddressesByAdmin(userId))
                .build();
    }
}
