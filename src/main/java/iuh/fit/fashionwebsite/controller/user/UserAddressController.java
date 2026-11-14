package iuh.fit.fashionwebsite.controller.user;

import iuh.fit.fashionwebsite.dto.request.user.UserAddressCreationRequest;
import iuh.fit.fashionwebsite.dto.request.user.UserAddressUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.user.UserAddressResponse;
import iuh.fit.fashionwebsite.service.user.UserAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;

    // Thêm địa chỉ mới cho người dùng đang đăng nhập
    @PostMapping({"/me/addresses", "/addresses"})
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserAddressResponse> createAddress(
            @RequestBody @Valid UserAddressCreationRequest request
    ) {
        return ApiResponse.<UserAddressResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Address has been added successfully")
                .data(userAddressService.createAddress(request))
                .build();
    }

    // Lấy danh sách địa chỉ của người dùng đang đăng nhập
    @GetMapping({"/me/addresses", "/addresses"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<List<UserAddressResponse>> getMyAddresses() {
        return ApiResponse.<List<UserAddressResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Fetched user addresses successfully")
                .data(userAddressService.getMyAddresses())
                .build();
    }

    // Lấy địa chỉ mặc định của người dùng đang đăng nhập
    @GetMapping({"/me/addresses/default", "/addresses/default"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserAddressResponse> getDefaultAddress() {
        return ApiResponse.<UserAddressResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Fetched default address successfully")
                .data(userAddressService.getDefaultAddress())
                .build();
    }

    // Lấy chi tiết 1 địa chỉ của chính mình (service kiểm tra owner)
    @GetMapping({"/me/addresses/{id}", "/addresses/{id}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserAddressResponse> getMyAddressById(@PathVariable Long id) {
        return ApiResponse.<UserAddressResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Fetched address detail successfully")
                .data(userAddressService.getMyAddressById(id))
                .build();
    }

    // Cập nhật địa chỉ của chính mình (service kiểm tra owner)
    @PutMapping({"/me/addresses/{id}", "/addresses/{id}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserAddressResponse> updateAddress(
            @PathVariable Long id,
            @RequestBody @Valid UserAddressUpdateRequest request
    ) {
        return ApiResponse.<UserAddressResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Address has been updated successfully")
                .data(userAddressService.updateAddress(id, request))
                .build();
    }

    // Xóa địa chỉ của chính mình (service kiểm tra owner)
    @DeleteMapping({"/me/addresses/{id}", "/addresses/{id}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<Void> deleteAddress(@PathVariable Long id) {
        userAddressService.deleteAddress(id);
        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Address has been deleted successfully")
                .build();
    }

    // Đặt địa chỉ làm mặc định (service kiểm tra owner)
    @PatchMapping({"/me/addresses/{id}/default", "/addresses/{id}/default"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserAddressResponse> setDefaultAddress(@PathVariable Long id) {
        return ApiResponse.<UserAddressResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Default address updated successfully")
                .data(userAddressService.setDefaultAddress(id))
                .build();
    }

    // ADMIN và STAFF xem danh sách địa chỉ của một user (service kiểm tra user tồn tại)
    @GetMapping("/{userId}/addresses")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<UserAddressResponse>> getUserAddressesByAdmin(@PathVariable Long userId) {
        return ApiResponse.<List<UserAddressResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Fetched user addresses by admin/staff successfully")
                .data(userAddressService.getUserAddressesByAdmin(userId))
                .build();
    }

    // ADMIN và STAFF thêm địa chỉ mới cho một user cụ thể
    @PostMapping("/{userId}/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<UserAddressResponse> createAddressForUser(
            @PathVariable Long userId,
            @RequestBody @Valid UserAddressCreationRequest request
    ) {
        return ApiResponse.<UserAddressResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Address has been added for user successfully")
                .data(userAddressService.createAddressForUser(userId, request))
                .build();
    }

    // ADMIN và STAFF xem chi tiết 1 địa chỉ của một user (service kiểm tra user tồn tại & địa chỉ thuộc user)
    @GetMapping("/{userId}/addresses/{addressId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<UserAddressResponse> getUserAddressByIdByAdmin(
            @PathVariable Long userId,
            @PathVariable Long addressId
    ) {
        return ApiResponse.<UserAddressResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Fetched address detail by admin/staff successfully")
                .data(userAddressService.getUserAddressByIdByAdmin(userId, addressId))
                .build();
    }
}
