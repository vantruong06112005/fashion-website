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
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.avt = :avt WHERE u.id = :id")
    void updateAvatar(@Param("id") Long id, @Param("avt") String avt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.passwordHash = :passwordHash, " +
            "u.passwordChangedAt = :changedAt, " +
            "u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    int updatePassword(@Param("id") Long id,
                       @Param("passwordHash") String passwordHash,
                       @Param("changedAt") java.time.Instant changedAt);

    @Query("SELECT u FROM User u WHERE " +
            "u.deletedAt IS NULL AND " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.fullname) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:gender IS NULL OR u.gender = :gender) AND " +
            "(:status IS NULL OR u.status = :status)")
    org.springframework.data.domain.Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("role") iuh.fit.fashionwebsite.enums.RoleUser role,
            @Param("gender") iuh.fit.fashionwebsite.enums.Gender gender,
            @Param("status") iuh.fit.fashionwebsite.enums.StatusUser status,
            org.springframework.data.domain.Pageable pageable);
}


