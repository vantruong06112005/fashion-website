package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.request.user.UserAddressCreationRequest;
import iuh.fit.fashionwebsite.dto.request.user.UserAddressUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.user.UserAddressResponse;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.entity.UserAddress;
import iuh.fit.fashionwebsite.enums.AddressType;
import iuh.fit.fashionwebsite.exception.user.UserAddressNotFoundException;
import iuh.fit.fashionwebsite.exception.user.UserNotFoundException;
import iuh.fit.fashionwebsite.mapper.UserAddressMapper;
import iuh.fit.fashionwebsite.repository.UserAddressRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceTest {

    @Mock
    private UserAddressRepository userAddressRepository;

    @Mock
    private UserAddressMapper userAddressMapper;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserAddressService userAddressService;

    private User currentUser;
    private UserAddress mockAddress;
    private UserAddressResponse mockResponse;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .id(1L)
                .username("testuser")
                .fullname("Test User")
                .build();

        mockAddress = UserAddress.builder()
                .id(10L)
                .fullAddress("123 Test Street")
                .provinceName("Hồ Chí Minh")
                .wardName("Phường Bến Nghé")
                .addressType(AddressType.HOME)
                .isDefault(false)
                .user(currentUser)
                .build();

        mockResponse = UserAddressResponse.builder()
                .id(10L)
                .fullAddress("123 Test Street")
                .provinceName("Hồ Chí Minh")
                .wardName("Phường Bến Nghé")
                .addressType(AddressType.HOME)
                .isDefault(false)
                .userId(1L)
                .build();
    }

    @Test
    @DisplayName("Tạo địa chỉ đầu tiên của user -> tự động đặt isDefault = true")
    void createAddress_firstAddress_automaticallyDefault() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        when(userAddressRepository.existsByUserId(1L)).thenReturn(false);

        UserAddressCreationRequest request = UserAddressCreationRequest.builder()
                .fullAddress("123 Test Street")
                .provinceName("Hồ Chí Minh")
                .wardName("Phường Bến Nghé")
                .addressType(AddressType.HOME)
                .isDefault(false)
                .build();

        UserAddress entity = UserAddress.builder()
                .fullAddress("123 Test Street")
                .provinceName("Hồ Chí Minh")
                .wardName("Phường Bến Nghé")
                .addressType(AddressType.HOME)
                .build();

        when(userAddressMapper.toUserAddress(request)).thenReturn(entity);
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAddressMapper.toUserAddressResponse(any(UserAddress.class))).thenReturn(mockResponse);

        UserAddressResponse result = userAddressService.createAddress(request);

        assertNotNull(result);
        assertTrue(entity.getIsDefault());
        assertEquals(currentUser, entity.getUser());
        verify(userAddressRepository, never()).resetDefaultAddressByUserId(anyLong());
        verify(userAddressRepository).save(entity);
    }

    @Test
    @DisplayName("Tạo địa chỉ tiếp theo với isDefault = true -> reset default cũ và lưu mới")
    void createAddress_subsequentAddress_withDefaultTrue_resetsExisting() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        when(userAddressRepository.existsByUserId(1L)).thenReturn(true);

        UserAddressCreationRequest request = UserAddressCreationRequest.builder()
                .fullAddress("456 Second Street")
                .provinceName("Hồ Chí Minh")
                .wardName("Phường Bến Nghé")
                .addressType(AddressType.OFFICE)
                .isDefault(true)
                .build();

        UserAddress entity = UserAddress.builder().build();

        when(userAddressMapper.toUserAddress(request)).thenReturn(entity);
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAddressMapper.toUserAddressResponse(any(UserAddress.class))).thenReturn(mockResponse);

        UserAddressResponse result = userAddressService.createAddress(request);

        assertNotNull(result);
        verify(userAddressRepository).resetDefaultAddressByUserId(1L);
        assertTrue(entity.getIsDefault());
        assertEquals(currentUser, entity.getUser());
    }

    @Test
    @DisplayName("Lấy danh sách địa chỉ của user")
    void getMyAddresses_returnsList() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        when(userAddressRepository.findAllByUserId(1L)).thenReturn(List.of(mockAddress));
        when(userAddressMapper.toUserAddressResponse(mockAddress)).thenReturn(mockResponse);

        List<UserAddressResponse> result = userAddressService.getMyAddresses();

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }

    @Test
    @DisplayName("Lấy địa chỉ theo ID thành công khi là owner")
    void getMyAddressById_found() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        when(userAddressRepository.findById(10L)).thenReturn(Optional.of(mockAddress));
        when(userAddressMapper.toUserAddressResponse(mockAddress)).thenReturn(mockResponse);

        UserAddressResponse result = userAddressService.getMyAddressById(10L);

        assertNotNull(result);
        assertEquals(10L, result.getId());
    }

    @Test
    @DisplayName("Lấy địa chỉ theo ID không tồn tại -> ném UserAddressNotFoundException")
    void getMyAddressById_notFound_throwsException() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        when(userAddressRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserAddressNotFoundException.class, () -> userAddressService.getMyAddressById(99L));
    }

    @Test
    @DisplayName("Lấy địa chỉ của user khác -> ràng buộc owner ném AccessDeniedException")
    void getMyAddressById_notOwner_throwsAccessDeniedException() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        User otherUser = User.builder().id(999L).build();
        UserAddress otherAddress = UserAddress.builder().id(10L).user(otherUser).build();
        when(userAddressRepository.findById(10L)).thenReturn(Optional.of(otherAddress));

        assertThrows(AccessDeniedException.class, () -> userAddressService.getMyAddressById(10L));
    }

    @Test
    @DisplayName("Lấy địa chỉ mặc định khi có địa chỉ default")
    void getDefaultAddress_defaultExists() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        mockAddress.setIsDefault(true);
        when(userAddressRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(mockAddress));
        when(userAddressMapper.toUserAddressResponse(mockAddress)).thenReturn(mockResponse);

        UserAddressResponse result = userAddressService.getDefaultAddress();

        assertNotNull(result);
        verify(userAddressRepository).findByUserIdAndIsDefaultTrue(1L);
    }

    @Test
    @DisplayName("Cập nhật địa chỉ thành mặc định -> reset default cũ")
    void updateAddress_setToDefault_resetsOthers() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        mockAddress.setIsDefault(false);
        when(userAddressRepository.findById(10L)).thenReturn(Optional.of(mockAddress));

        UserAddressUpdateRequest request = UserAddressUpdateRequest.builder()
                .fullAddress("New Address")
                .isDefault(true)
                .build();

        when(userAddressRepository.save(mockAddress)).thenReturn(mockAddress);
        when(userAddressMapper.toUserAddressResponse(mockAddress)).thenReturn(mockResponse);

        UserAddressResponse result = userAddressService.updateAddress(10L, request);

        assertNotNull(result);
        verify(userAddressRepository).resetDefaultAddressByUserId(1L);
        assertTrue(mockAddress.getIsDefault());
        verify(userAddressMapper).updateUserAddress(mockAddress, request);
    }

    @Test
    @DisplayName("Cập nhật địa chỉ không thuộc sở hữu -> ném AccessDeniedException")
    void updateAddress_notOwner_throwsAccessDeniedException() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        User otherUser = User.builder().id(999L).build();
        UserAddress otherAddress = UserAddress.builder().id(10L).user(otherUser).build();
        when(userAddressRepository.findById(10L)).thenReturn(Optional.of(otherAddress));

        UserAddressUpdateRequest request = UserAddressUpdateRequest.builder().build();

        assertThrows(AccessDeniedException.class, () -> userAddressService.updateAddress(10L, request));
    }

    @Test
    @DisplayName("Xóa địa chỉ mặc định -> tự động chuyển địa chỉ còn lại thành mặc định")
    void deleteAddress_wasDefault_promotesNextAddress() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        mockAddress.setIsDefault(true);
        when(userAddressRepository.findById(10L)).thenReturn(Optional.of(mockAddress));

        UserAddress remainingAddress = UserAddress.builder()
                .id(20L)
                .isDefault(false)
                .user(currentUser)
                .build();

        List<UserAddress> remainingList = new ArrayList<>(List.of(remainingAddress));
        when(userAddressRepository.findAllByUserId(1L)).thenReturn(remainingList);

        userAddressService.deleteAddress(10L);

        verify(userAddressRepository).delete(mockAddress);
        verify(userAddressRepository).flush();
        assertTrue(remainingAddress.getIsDefault());
        verify(userAddressRepository).save(remainingAddress);
    }

    @Test
    @DisplayName("Xóa địa chỉ không thuộc sở hữu -> ném AccessDeniedException")
    void deleteAddress_notOwner_throwsAccessDeniedException() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        User otherUser = User.builder().id(999L).build();
        UserAddress otherAddress = UserAddress.builder().id(10L).user(otherUser).build();
        when(userAddressRepository.findById(10L)).thenReturn(Optional.of(otherAddress));

        assertThrows(AccessDeniedException.class, () -> userAddressService.deleteAddress(10L));
    }

    @Test
    @DisplayName("Đặt địa chỉ làm mặc định nhanh")
    void setDefaultAddress_success() {
        when(userService.getCurrentUserEntity()).thenReturn(currentUser);
        mockAddress.setIsDefault(false);
        when(userAddressRepository.findById(10L)).thenReturn(Optional.of(mockAddress));
        when(userAddressRepository.save(mockAddress)).thenReturn(mockAddress);
        when(userAddressMapper.toUserAddressResponse(mockAddress)).thenReturn(mockResponse);

        UserAddressResponse result = userAddressService.setDefaultAddress(10L);

        assertNotNull(result);
        verify(userAddressRepository).resetDefaultAddressByUserId(1L);
        assertTrue(mockAddress.getIsDefault());
    }

    @Test
    @DisplayName("ADMIN xem địa chỉ của user tồn tại")
    void getUserAddressesByAdmin_userExists() {
        when(userRepository.existsById(5L)).thenReturn(true);
        when(userAddressRepository.findAllByUserId(5L)).thenReturn(List.of(mockAddress));
        when(userAddressMapper.toUserAddressResponse(mockAddress)).thenReturn(mockResponse);

        List<UserAddressResponse> result = userAddressService.getUserAddressesByAdmin(5L);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("ADMIN xem chi tiết địa chỉ thuộc đúng user")
    void getUserAddressByIdByAdmin_success() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(userAddressRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(mockAddress));
        when(userAddressMapper.toUserAddressResponse(mockAddress)).thenReturn(mockResponse);

        assertEquals(10L, userAddressService.getUserAddressByIdByAdmin(1L, 10L).getId());
    }

    @Test
    @DisplayName("ADMIN xem địa chỉ không thuộc user chỉ định -> ném UserAddressNotFoundException")
    void getUserAddressByIdByAdmin_addressNotBelongToUser() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(userAddressRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThrows(UserAddressNotFoundException.class,
                () -> userAddressService.getUserAddressByIdByAdmin(2L, 10L));
    }

    @Test
    @DisplayName("ADMIN xem địa chỉ của user không tồn tại -> ném UserNotFoundException")
    void getUserAddressesByAdmin_userNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> userAddressService.getUserAddressesByAdmin(99L));
    }
}
