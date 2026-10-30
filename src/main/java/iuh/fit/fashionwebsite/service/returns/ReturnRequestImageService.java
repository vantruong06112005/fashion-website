package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestImageService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final CloudinaryService cloudinaryService;

    public void validate(List<MultipartFile> images) {
        if (images.size() > 5) {
            throw new AppException(ReturnRequestErrorCode.TOO_MANY_IMAGES);
        }
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty() || image.getSize() > MAX_BYTES) {
                throw new AppException(ReturnRequestErrorCode.INVALID_IMAGE);
            }
            String filename = image.getOriginalFilename();
            if (filename != null && !filename.isBlank() && filename.contains(".")) {
                String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                if (!List.of("jpg", "jpeg", "png", "webp").contains(ext)) {
                    throw new AppException(ReturnRequestErrorCode.INVALID_IMAGE);
                }
            }
            // Không tin phần mở rộng tên tệp hoặc Content-Type do client gửi.
            try (var input = image.getInputStream()) {
                byte[] header = input.readNBytes(12);
                String mime = image.getContentType();
                boolean jpeg = header.length >= 3 && header[0] == (byte) 0xff
                        && header[1] == (byte) 0xd8 && header[2] == (byte) 0xff;
                boolean png = header.length >= 8 && header[0] == (byte) 0x89
                        && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                        && header[4] == 13 && header[5] == 10 && header[6] == 26 && header[7] == 10;
                boolean webp = header.length == 12 && header[0] == 'R' && header[1] == 'I'
                        && header[2] == 'F' && header[3] == 'F' && header[8] == 'W'
                        && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
                if (!(jpeg && ("image/jpeg".equals(mime) || "image/jpg".equals(mime)))
                        && !(png && "image/png".equals(mime))
                        && !(webp && "image/webp".equals(mime))) {
                    throw new AppException(ReturnRequestErrorCode.INVALID_IMAGE);
                }
            } catch (IOException e) {
                throw new AppException(ReturnRequestErrorCode.INVALID_IMAGE);
            }
        }
    }

    /** Chỉ gọi sau khi kiểm tra quyền sở hữu, hạn trả và số lượng thành công. */
    public List<String> upload(List<MultipartFile> images) {
        validate(images);
        List<String> uploaded = new ArrayList<>();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        cleanup(uploaded);
                    }
                }
            });
        }
        try {
            for (MultipartFile image : images) {
                // Cloudinary giải mã ảnh thật; tệp chỉ giả chữ ký nhưng hỏng sẽ bị từ chối.
                String url = cloudinaryService.uploadImage(image, "fashion_website/returns");
                if (url == null || url.isBlank()) {
                    throw new AppException(ReturnRequestErrorCode.IMAGE_UPLOAD_FAILED);
                }
                uploaded.add(url);
                if (url.length() > 255) {
                    throw new AppException(ReturnRequestErrorCode.IMAGE_UPLOAD_FAILED);
                }
            }
            return List.copyOf(uploaded);
        } catch (RuntimeException e) {
            cleanup(uploaded);
            throw new AppException(ReturnRequestErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    /**
     * Clean-up Pattern: Khách đổi ảnh -> Upload ảnh mới thành công -> Lưu DB -> Xóa ảnh cũ trên Cloudinary.
     */
    public void deleteOldImagesAfterCommit(List<String> oldUrls) {
        if (oldUrls == null || oldUrls.isEmpty()) {
            return;
        }
        List<String> urlsToDelete = new ArrayList<>(oldUrls);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup(urlsToDelete);
                }
            });
        } else {
            cleanup(urlsToDelete);
        }
    }

    private void cleanup(List<String> urls) {
        for (String url : urls) {
            try {
                cloudinaryService.deleteImage(url);
            } catch (RuntimeException e) {
                log.warn("Không thể dọn ảnh trả hàng trên Cloudinary.", e);
            }
        }
        urls.clear();
    }
}
