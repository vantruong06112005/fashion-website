package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.request.user.UserAddressCreationRequest;
import iuh.fit.fashionwebsite.dto.request.user.UserAddressUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.user.UserAddressResponse;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.entity.UserAddress;
import iuh.fit.fashionwebsite.exception.user.UserAddressNotFoundException;
import iuh.fit.fashionwebsite.exception.user.UserNotFoundException;
import iuh.fit.fashionwebsite.mapper.UserAddressMapper;
import iuh.fit.fashionwebsite.repository.UserAddressRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserAddressService {

    private final UserAddressRepository userAddressRepository;
    private final UserAddressMapper userAddressMapper;
    private final UserService userService;
    private final UserRepository userRepository;

    public UserAddressResponse createAddress(UserAddressCreationRequest request) {
        User currentUser = userService.getCurrentUserEntity();
        boolean hasAddresses = userAddressRepository.existsByUserId(currentUser.getId());

        // Ràng buộc: Địa chỉ đầu tiên luôn tự động là địa chỉ mặc định
        boolean isDefault = !hasAddresses || Boolean.TRUE.equals(request.getIsDefault());

        // Ràng buộc: Nếu đặt làm mặc định và đã có địa chỉ trước đó, reset toàn bộ địa chỉ cũ
        if (isDefault && hasAddresses) {
            userAddressRepository.resetDefaultAddressByUserId(currentUser.getId());
        }

        UserAddress address = userAddressMapper.toUserAddress(request);
        address.setUser(currentUser);
        address.setIsDefault(isDefault);

        return userAddressMapper.toUserAddressResponse(
                userAddressRepository.save(address)
        );
    }

    public UserAddressResponse createAddressForUser(Long userId, UserAddressCreationRequest request) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        boolean hasAddresses = userAddressRepository.existsByUserId(userId);

        // Ràng buộc: Địa chỉ đầu tiên luôn tự động là địa chỉ mặc định
        boolean isDefault = !hasAddresses || Boolean.TRUE.equals(request.getIsDefault());

        // Ràng buộc: Nếu đặt làm mặc định và đã có địa chỉ trước đó, reset toàn bộ địa chỉ cũ
        if (isDefault && hasAddresses) {
            userAddressRepository.resetDefaultAddressByUserId(userId);
        }

        UserAddress address = userAddressMapper.toUserAddress(request);
        address.setUser(targetUser);
        address.setIsDefault(isDefault);

        return userAddressMapper.toUserAddressResponse(
                userAddressRepository.save(address)
        );
    }

    @Transactional(readOnly = true)
    public List<UserAddressResponse> getMyAddresses() {
        User currentUser = userService.getCurrentUserEntity();
        return userAddressRepository.findAllByUserId(currentUser.getId())
                .stream()
                .map(userAddressMapper::toUserAddressResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserAddressResponse getMyAddressById(Long id) {
        User currentUser = userService.getCurrentUserEntity();
        // Ràng buộc: Kiểm tra địa chỉ tồn tại và kiểm tra quyền sở hữu (owner)
        UserAddress address = getAddressAndVerifyOwner(id, currentUser);

        return userAddressMapper.toUserAddressResponse(address);
    }

    @Transactional(readOnly = true)
    public UserAddressResponse getDefaultAddress() {
        User currentUser = userService.getCurrentUserEntity();
        UserAddress defaultAddress = userAddressRepository.findByUserIdAndIsDefaultTrue(currentUser.getId())
                .orElseGet(() -> {
                    List<UserAddress> addresses = userAddressRepository.findAllByUserId(currentUser.getId());
                    if (addresses.isEmpty()) {
                        throw new UserAddressNotFoundException();
                    }
                    return addresses.get(0);
                });

        return userAddressMapper.toUserAddressResponse(defaultAddress);
    }

    public UserAddressResponse updateAddress(Long id, UserAddressUpdateRequest request) {
        User currentUser = userService.getCurrentUserEntity();
        // Ràng buộc: Kiểm tra địa chỉ tồn tại và kiểm tra quyền sở hữu (owner) hoặc là ADMIN
        UserAddress address = getAddressAndVerifyOwner(id, currentUser);

        // Ràng buộc địa chỉ mặc định
        Long targetUserId = address.getUser() != null ? address.getUser().getId() : currentUser.getId();
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            if (!Boolean.TRUE.equals(address.getIsDefault())) {
                userAddressRepository.resetDefaultAddressByUserId(targetUserId);
                address.setIsDefault(true);
            }
        } else if (Boolean.FALSE.equals(request.getIsDefault())) {
            // Ràng buộc: Nếu địa chỉ này đang là mặc định, không tự động bỏ mặc định (tránh trường hợp user không có địa chỉ mặc định)
            if (Boolean.TRUE.equals(address.getIsDefault())) {
                address.setIsDefault(true);
            }
        }

        userAddressMapper.updateUserAddress(address, request);

        return userAddressMapper.toUserAddressResponse(
                userAddressRepository.save(address)
        );
    }

    public void deleteAddress(Long id) {
        User currentUser = userService.getCurrentUserEntity();
        // Ràng buộc: Kiểm tra địa chỉ tồn tại và kiểm tra quyền sở hữu (owner) hoặc là ADMIN
        UserAddress address = getAddressAndVerifyOwner(id, currentUser);

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        Long targetUserId = address.getUser() != null ? address.getUser().getId() : currentUser.getId();
        userAddressRepository.delete(address);
        userAddressRepository.flush();

        // Ràng buộc: Nếu xoá địa chỉ mặc định, tự động chuyển địa chỉ còn lại mới nhất thành mặc định
        if (wasDefault) {
            List<UserAddress> remaining = userAddressRepository.findAllByUserId(targetUserId);
            if (!remaining.isEmpty()) {
                UserAddress newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                userAddressRepository.save(newDefault);
            }
        }
    }

    public UserAddressResponse setDefaultAddress(Long id) {
        User currentUser = userService.getCurrentUserEntity();
        // Ràng buộc: Kiểm tra địa chỉ tồn tại và kiểm tra quyền sở hữu (owner) hoặc là ADMIN
        UserAddress address = getAddressAndVerifyOwner(id, currentUser);

        Long targetUserId = address.getUser() != null ? address.getUser().getId() : currentUser.getId();
        if (!Boolean.TRUE.equals(address.getIsDefault())) {
            userAddressRepository.resetDefaultAddressByUserId(targetUserId);
            address.setIsDefault(true);
            address = userAddressRepository.save(address);
        }

        return userAddressMapper.toUserAddressResponse(address);
    }

    @Transactional(readOnly = true)
    public List<UserAddressResponse> getUserAddressesByAdmin(Long userId) {
        // Ràng buộc: Kiểm tra user có tồn tại trong hệ thống không
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }

        return userAddressRepository.findAllByUserId(userId)
                .stream()
                .map(userAddressMapper::toUserAddressResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserAddressResponse getUserAddressByIdByAdmin(Long userId, Long addressId) {
        // Ràng buộc: Kiểm tra user có tồn tại trong hệ thống không
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }
        // Ràng buộc: Địa chỉ phải tồn tại và thuộc về đúng user được chỉ định
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(UserAddressNotFoundException::new);
        return userAddressMapper.toUserAddressResponse(address);
    }

    /**
     * Ràng buộc nghiệp vụ: Kiểm tra địa chỉ có tồn tại và thuộc sở hữu của người dùng hiện tại không.
     * - Ném UserAddressNotFoundException nếu địa chỉ không tồn tại (HTTP 404).
     * - Ném AccessDeniedException nếu địa chỉ thuộc về người dùng khác (HTTP 403).
     */
    private UserAddress getAddressAndVerifyOwner(Long addressId, User currentUser) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(UserAddressNotFoundException::new);

        boolean isAdmin = currentUser.getRole() == iuh.fit.fashionwebsite.enums.RoleUser.ADMIN;
        if (!isAdmin && (address.getUser() == null || !Objects.equals(address.getUser().getId(), currentUser.getId()))) {
            throw new AccessDeniedException("Access denied: You do not have permission to access or modify this address");
        }

        return address;
    }
}
