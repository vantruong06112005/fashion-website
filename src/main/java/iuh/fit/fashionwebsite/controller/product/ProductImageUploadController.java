package iuh.fit.fashionwebsite.controller.product;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
@RestController @RequestMapping("/products/images") @RequiredArgsConstructor
public class ProductImageUploadController {
    private final CloudinaryService cloudinary;
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024 ||
                !java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(file.getContentType() == null ? "" : file.getContentType()))
            throw new AppException("Ảnh phải là JPEG/PNG/WebP và không quá 5MB");
        return ApiResponse.<Map<String, String>>builder().code(200).data(Map.of("imageUrl",
                cloudinary.uploadImage(file, "fashion_website/products"))).build();
    }
}
