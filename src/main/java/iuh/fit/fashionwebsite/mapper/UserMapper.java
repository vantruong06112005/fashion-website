/*
 * @ (#) UserMapper.java     1.0    8/25/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.mapper;


import iuh.fit.fashionwebsite.dto.request.UserCreationRequest;
import iuh.fit.fashionwebsite.dto.request.UserUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.UserResponse;
import iuh.fit.fashionwebsite.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/*
 * @description
 * @author:NguyenTruong
 * @date:  8/25/2026
 * @version:    1.0
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "addresses", ignore = true)
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "cart", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "returnRequests", ignore = true)
    User toUser(UserCreationRequest request);

    UserResponse toUserResponse(User user);

    @Mapping(target = "id", ignore = true) //“field này MapStruct không được tự map, tôi sẽ tự xử lý nó.”
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "addresses", ignore = true)
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "cart", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "returnRequests", ignore = true)
    void updateUser(
            @MappingTarget User user,
            UserUpdateRequest request
    );
}