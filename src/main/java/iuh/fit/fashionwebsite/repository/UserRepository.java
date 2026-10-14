/*
 * @ (#) UserRepository.java     1.0    8/29/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.repository;


import iuh.fit.fashionwebsite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.avt = :avt WHERE u.id = :id")
    void updateAvatar(@Param("id") Long id, @Param("avt") String avt);
}


