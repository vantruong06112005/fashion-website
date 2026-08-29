package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "fullname")
    private String fullname;

    @Column(name = "phone")
    private String phone;
    @Column(name = "email", unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private RoleUser role;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusUser status;
}