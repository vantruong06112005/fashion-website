/*
 * @ (#) InitData.java     1.0    8/25/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.config;

import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.entity.UserAddress;
import iuh.fit.fashionwebsite.enums.AddressType;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import iuh.fit.fashionwebsite.repository.UserAddressRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitData implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.default-password}")
    private String defaultPassword;

    @Override
    public void run(String... args) throws Exception {

        if (userRepository.count() > 0) {
            return;
        }

        String password = passwordEncoder.encode(defaultPassword);

        User admin01 = userRepository.save(User.builder()
                .username("admin01")
                .passwordHash(password)
                .fullname("Nguyen Van Admin")
                .phone("0901000001")
                .email("admin01@gmail.com")
                .role(RoleUser.ADMIN)
                .status(StatusUser.ACTIVE)
                .build());

        User staff01 = userRepository.save(User.builder()
                .username("staff01")
                .passwordHash(password)
                .fullname("Tran Thi Staff")
                .phone("0901000099")
                .email("staff01@gmail.com")
                .role(RoleUser.STAFF)
                .status(StatusUser.ACTIVE)
                .build());

        User truong01 = userRepository.save(User.builder()
                .username("truong01")
                .passwordHash(password)
                .fullname("Nguyen Van Truong")
                .phone("0901000002")
                .email("truong01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        User an01 = userRepository.save(User.builder()
                .username("an01")
                .passwordHash(password)
                .fullname("Tran Van An")
                .phone("0901000003")
                .email("an01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        User binh01 = userRepository.save(User.builder()
                .username("binh01")
                .passwordHash(password)
                .fullname("Le Van Binh")
                .phone("0901000004")
                .email("binh01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .username("cuong01")
                .passwordHash(password)
                .fullname("Pham Van Cuong")
                .phone("0901000005")
                .email("cuong01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .username("dung01")
                .passwordHash(password)
                .fullname("Hoang Van Dung")
                .phone("0901000006")
                .email("dung01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .username("ha01")
                .passwordHash(password)
                .fullname("Nguyen Thi Ha")
                .phone("0901000007")
                .email("ha01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .username("lan01")
                .passwordHash(password)
                .fullname("Tran Thi Lan")
                .phone("0901000008")
                .email("lan01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .username("mai01")
                .passwordHash(password)
                .fullname("Le Thi Mai")
                .phone("0901000009")
                .email("mai01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .username("nam01")
                .passwordHash(password)
                .fullname("Pham Van Nam")
                .phone("0901000010")
                .email("nam01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        // Khởi tạo địa chỉ mẫu cho người dùng
        userAddressRepository.save(UserAddress.builder()
                .user(truong01)
                .fullAddress("12 Nguyễn Văn Bảo, Phường 4, Quận Gò Vấp, TP. Hồ Chí Minh")
                .addressType(AddressType.HOME)
                .isDefault(true)
                .build());

        userAddressRepository.save(UserAddress.builder()
                .user(truong01)
                .fullAddress("Tòa nhà Bitexco, 2 Hải Triều, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                .addressType(AddressType.OFFICE)
                .isDefault(false)
                .build());

        userAddressRepository.save(UserAddress.builder()
                .user(an01)
                .fullAddress("45 Lê Lợi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh")
                .addressType(AddressType.HOME)
                .isDefault(true)
                .build());

        userAddressRepository.save(UserAddress.builder()
                .user(binh01)
                .fullAddress("123 Quang Trung, Phường 10, Quận Gò Vấp, TP. Hồ Chí Minh")
                .addressType(AddressType.HOME)
                .isDefault(true)
                .build());

        userAddressRepository.save(UserAddress.builder()
                .user(staff01)
                .fullAddress("Văn phòng Fashion Shop, 80 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                .addressType(AddressType.OFFICE)
                .isDefault(true)
                .build());
    }
}