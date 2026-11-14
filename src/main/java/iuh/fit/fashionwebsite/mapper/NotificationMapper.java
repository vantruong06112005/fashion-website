package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.response.NotificationResponse;
import iuh.fit.fashionwebsite.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponse toResponse(Notification entity);
}
