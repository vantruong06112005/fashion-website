package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.request.UserAddressCreationRequest;
import iuh.fit.fashionwebsite.dto.request.UserAddressUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.UserAddressResponse;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.entity.UserAddress;
import iuh.fit.fashionwebsite.exception.user.AddressNotFoundException;
import iuh.fit.fashionwebsite.exception.user.UserNotFoundException;
import iuh.fit.fashionwebsite.mapper.UserAddressMapper;
import iuh.fit.fashionwebsite.repository.UserAddressRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        boolean isDefault = !hasAddresses || Boolean.TRUE.equals(request.getIsDefault());

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
        UserAddress address = userAddressRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(AddressNotFoundException::new);

        return userAddressMapper.toUserAddressResponse(address);
    }

    public UserAddressResponse updateAddress(Long id, UserAddressUpdateRequest request) {
        User currentUser = userService.getCurrentUserEntity();
        UserAddress address = userAddressRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(AddressNotFoundException::new);

        if (Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(address.getIsDefault())) {
            userAddressRepository.resetDefaultAddressByUserId(currentUser.getId());
            address.setIsDefault(true);
        }

        userAddressMapper.updateUserAddress(address, request);

        return userAddressMapper.toUserAddressResponse(
                userAddressRepository.save(address)
        );
    }

    public void deleteAddress(Long id) {
        User currentUser = userService.getCurrentUserEntity();
        UserAddress address = userAddressRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(AddressNotFoundException::new);

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        userAddressRepository.delete(address);

        // Nếu xoá địa chỉ mặc định, tự động chuyển địa chỉ còn lại đầu tiên thành mặc định
        if (wasDefault) {
            List<UserAddress> remaining = userAddressRepository.findAllByUserId(currentUser.getId());
            if (!remaining.isEmpty()) {
                UserAddress newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                userAddressRepository.save(newDefault);
            }
        }
    }

    public UserAddressResponse setDefaultAddress(Long id) {
        User currentUser = userService.getCurrentUserEntity();
        UserAddress address = userAddressRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(AddressNotFoundException::new);

        userAddressRepository.resetDefaultAddressByUserId(currentUser.getId());
        address.setIsDefault(true);

        return userAddressMapper.toUserAddressResponse(
                userAddressRepository.save(address)
        );
    }

    @Transactional(readOnly = true)
    public List<UserAddressResponse> getUserAddressesByAdmin(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }

        return userAddressRepository.findAllByUserId(userId)
                .stream()
                .map(userAddressMapper::toUserAddressResponse)
                .toList();
    }
}
