package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.UserAddressCreationRequest;
import iuh.fit.fashionwebsite.dto.request.UserAddressUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.UserAddressResponse;
import iuh.fit.fashionwebsite.entity.UserAddress;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserAddressMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    UserAddress toUserAddress(UserAddressCreationRequest request);

    @Mapping(target = "userId", source = "user.id")
    UserAddressResponse toUserAddressResponse(UserAddress userAddress);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateUserAddress(@MappingTarget UserAddress userAddress, UserAddressUpdateRequest request);
}
