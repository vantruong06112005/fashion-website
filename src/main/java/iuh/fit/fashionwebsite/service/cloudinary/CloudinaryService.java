package iuh.fit.fashionwebsite.service.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    private String getEffectiveCloudName() {
        if (cloudName != null && !cloudName.isBlank()) {
            return cloudName.trim();
        }
        if (cloudinary != null && cloudinary.config != null && cloudinary.config.cloudName != null) {
            return cloudinary.config.cloudName.trim();
        }
        return null;
    }

    public String uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE);
        }

        String effectiveCloudName = getEffectiveCloudName();
        if (effectiveCloudName == null || effectiveCloudName.isBlank()) {
            log.error("Cloudinary cloud-name is missing in application.properties! Please configure cloudinary.cloud-name, cloudinary.api-key, and cloudinary.api-secret.");
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image"
                    )
            );
            return (String) uploadResult.get("secure_url");
        } catch (Exception e) {
            log.error("Failed to upload image to Cloudinary: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        String effectiveCloudName = getEffectiveCloudName();
        if (effectiveCloudName == null || effectiveCloudName.isBlank()) {
            log.warn("Cloudinary cloud-name is not configured. Skipping image deletion: {}", imageUrl);
            return;
        }

        String publicId = extractPublicId(imageUrl);
        if (publicId == null || publicId.isBlank()) {
            log.debug("Skipping image deletion - URL is not a recognized Cloudinary image: {}", imageUrl);
            return;
        }

        // Không xóa ảnh demo mẫu của hệ thống
        if (publicId.startsWith("fashion_website/demo/")) {
            log.info("Preserving demo seed asset, skipping deletion: {}", publicId);
            return;
        }

        try {
            Map<?, ?> result = cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap(
                            "invalidate", Boolean.TRUE,
                            "resource_type", "image"
                    )
            );
            String status = result != null ? String.valueOf(result.get("result")) : "unknown";
            if ("ok".equalsIgnoreCase(status)) {
                log.info("Successfully deleted image from Cloudinary (publicId: {})", publicId);
            } else {
                log.warn("Cloudinary destroy returned non-ok result for publicId '{}': {}", publicId, result);
            }
        } catch (Exception e) {
            log.error("Failed to delete image from Cloudinary (publicId: {}): {}", publicId, e.getMessage(), e);
        }
    }

    public String extractPublicId(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        imageUrl = imageUrl.trim();

        // Loại bỏ query parameter và fragment nếu có
        int queryIndex = imageUrl.indexOf('?');
        if (queryIndex != -1) {
            imageUrl = imageUrl.substring(0, queryIndex);
        }
        int hashIndex = imageUrl.indexOf('#');
        if (hashIndex != -1) {
            imageUrl = imageUrl.substring(0, hashIndex);
        }

        // If it's already a publicId path (does not start with http:// or https://)
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            int lastDot = imageUrl.lastIndexOf('.');
            return lastDot != -1 ? imageUrl.substring(0, lastDot) : imageUrl;
        }

        try {
            int uploadIndex = imageUrl.indexOf("/upload/");
            if (uploadIndex == -1) {
                return null;
            }

            String pathAfterUpload = imageUrl.substring(uploadIndex + "/upload/".length());

            // Remove transformation and/or version segment if present, e.g. "v1234567/" or "c_fill,w_100/v1234567/"
            if (pathAfterUpload.matches("^(?:[^/]+/)*?v\\d+/.+")) {
                pathAfterUpload = pathAfterUpload.replaceFirst("^(?:[^/]+/)*?v\\d+/", "");
            }

            // Remove file extension
            int lastDot = pathAfterUpload.lastIndexOf('.');
            if (lastDot != -1) {
                pathAfterUpload = pathAfterUpload.substring(0, lastDot);
            }

            return pathAfterUpload;
        } catch (Exception e) {
            log.warn("Failed to extract public ID from URL: {}", imageUrl, e);
            return null;
        }
    }
}
