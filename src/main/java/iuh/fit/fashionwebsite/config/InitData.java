/*
 * @ (#) InitData.java     1.0    8/25/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.config;

import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import iuh.fit.fashionwebsite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitData implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    @Override
    public void run(String... args) throws Exception {

        if (userRepository.count() > 0) {
            return;
        }

        String password = passwordEncoder.encode("123456");

        userRepository.save(User.builder()
                .username("admin01")
                .passwordHash(password)
                .fullname("Nguyen Van Admin")
                .phone("0901000001")
                .email("admin01@gmail.com")
                .role(RoleUser.ADMIN)
                .status(StatusUser.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .username("truong01")
                .passwordHash(password)
                .fullname("Nguyen Van Truong")
                .phone("0901000002")
                .email("truong01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .username("an01")
                .passwordHash(password)
                .fullname("Tran Van An")
                .phone("0901000003")
                .email("an01@gmail.com")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build());

        userRepository.save(User.builder()
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
    }
}