package iuh.fit.fashionwebsite.dto.response.shipping;

public record GhnResponse<T>(
        Integer code,
        String message,
        T data
) {}
