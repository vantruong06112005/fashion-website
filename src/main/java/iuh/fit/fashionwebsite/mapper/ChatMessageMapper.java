package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.response.ChatMessageResponse;
import iuh.fit.fashionwebsite.entity.ChatMessage;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChatMessageMapper {
    ChatMessageResponse toResponse(ChatMessage entity);
}
