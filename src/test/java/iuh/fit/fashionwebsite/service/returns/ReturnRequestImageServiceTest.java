package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Lớp kiểm thử đơn vị (Unit Test) cho {@link ReturnRequestImageService}.
 * <p>
 * Mục tiêu: kiểm tra các quy tắc bảo mật và hợp lệ khi tải ảnh minh chứng trả hàng
 * (giới hạn tối đa 5 ảnh, mỗi ảnh tối đa 5 MiB, kiểm tra chữ ký magic bytes thực tế của JPEG/PNG/WebP
 * để chống giả mạo MIME/SVG/đuôi file), cũng như cơ chế dọn rác ảnh trên Cloudinary khi upload lỗi,
 * khi transaction DB rollback, hoặc sau khi DB commit thay thế ảnh cũ.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestImageServiceTest {
    @Mock private CloudinaryService cloudinary;
    private ReturnRequestImageService service;

    /**
     * Khởi tạo {@link ReturnRequestImageService} với {@link CloudinaryService} giả lập trước mỗi test case.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Mock {@code cloudinary}.
     * <p>
     * Kỳ vọng: Đối tượng {@code service} được tạo mới độc lập cho từng test.
     */
    @BeforeEach
    void setUp() {
        service = new ReturnRequestImageService(cloudinary);
    }

    /**
     * Tạo nhanh một {@link MockMultipartFile} giả lập ảnh JPEG hợp lệ (chứa magic bytes {@code FF D8 FF}).
     *
     * @return file ảnh JPEG mẫu dùng cho các kịch bản upload hợp lệ
     */
    private MockMultipartFile jpeg() {
        // 3 byte đầu FF D8 FF là chữ ký chuẩn (magic bytes) nhận diện file JPEG thật
        return new MockMultipartFile("images", "evidence.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0});
    }

    /**
     * Kiểm thử giá trị biên hợp lệ: chấp nhận tối đa 5 file ảnh và dung lượng đúng bằng 5 MiB (5 * 1024 * 1024 byte).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Danh sách 5 bản sao của file JPEG có kích thước chính xác 5 MiB và magic bytes hợp lệ.
     * <p>
     * Kỳ vọng: {@code service.validate(...)} không ném bất kỳ ngoại lệ nào.
     */
    @Test
    void acceptsFiveFilesAndExactFiveMiBBoundary() {
        // Tạo mảng byte đúng 5 MiB kèm header JPEG để kiểm tra ngưỡng biên trên hợp lệ
        byte[] bytes = new byte[5 * 1024 * 1024];
        bytes[0] = (byte) 0xff; bytes[1] = (byte) 0xd8; bytes[2] = (byte) 0xff;
        var file = new MockMultipartFile("images", "a.jpg", "image/jpeg", bytes);
        assertDoesNotThrow(() -> service.validate(Collections.nCopies(5, file)));
    }

    /**
     * Kiểm thử từ chối khi gửi quá 5 file, file vượt quá 5 MiB hoặc file rỗng (0 byte), và không gọi Cloudinary.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: 6 file JPEG; 1 file 5 MiB + 1 byte; 1 file rỗng {@code new byte[0]}.
     * <p>
     * Kỳ vọng: Lần lượt ném {@code TOO_MANY_IMAGES}, {@code INVALID_IMAGE}, {@code INVALID_IMAGE}
     * và {@code cloudinary} không bị gọi lần nào.
     */
    @Test
    void rejectsSixFilesAndOversizedOrEmptyFilesWithoutUploading() {
        // Vượt quá giới hạn tối đa 5 ảnh cho mỗi phiếu trả hàng
        assertCode(ReturnRequestErrorCode.TOO_MANY_IMAGES, () -> service.upload(Collections.nCopies(6, jpeg())));
        // Vượt quá 5 MiB dù chỉ 1 byte -> bị chặn trước khi đẩy lên Cloudinary
        assertCode(ReturnRequestErrorCode.INVALID_IMAGE, () -> service.upload(List.of(
                new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]))));
        // Chặn file rỗng 0 byte để tránh lưu ảnh hỏng
        assertCode(ReturnRequestErrorCode.INVALID_IMAGE, () -> service.upload(List.of(
                new MockMultipartFile("images", new byte[0]))));
        verifyNoInteractions(cloudinary);
    }

    /**
     * Kiểm thử chống giả mạo định dạng: từ chối file SVG đội lốt JPEG/PNG/WebP (sai magic bytes)
     * và từ chối đuôi file không hợp lệ dù có magic bytes JPEG.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Nội dung {@code "<svg/>"} đặt tên {@code fake.jpg} với 4 Content-Type khác nhau;
     * và file tên {@code bad.gif} nhưng chứa magic bytes JPEG.
     * <p>
     * Kỳ vọng: Tất cả đều ném {@code INVALID_IMAGE} và không tương tác với {@code cloudinary}.
     */
    @Test
    void rejectsSpoofedMimeSvgInvalidExtensionAndWrongSignature() {
        // Kẻ tấn công có thể đổi đuôi .svg thành .jpg hoặc giả header Content-Type để chèn XSS; kiểm tra magic bytes sẽ chặn đứng
        for (String mime : List.of("image/jpeg", "image/png", "image/webp", "image/svg+xml")) {
            assertCode(ReturnRequestErrorCode.INVALID_IMAGE, () -> service.upload(List.of(
                    new MockMultipartFile("images", "fake.jpg", mime, "<svg/>".getBytes()))));
        }
        // Đuôi .gif không nằm trong whitelist (jpg/jpeg/png/webp) dù bên trong có bytes JPEG cũng bị từ chối
        assertCode(ReturnRequestErrorCode.INVALID_IMAGE, () -> service.upload(List.of(
                new MockMultipartFile("images", "bad.gif", "image/jpeg",
                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}))));
        verifyNoInteractions(cloudinary);
    }

    /**
     * Kiểm thử chấp nhận đầy đủ các định dạng ảnh hợp lệ (.jpeg/.jpg, .png, .webp) có đúng chữ ký magic bytes.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: 3 file chứa đúng magic bytes chuẩn của JPEG ({@code FF D8 FF}),
     * PNG ({@code 89 50 4E 47 0D 0A 1A 0A}) và WebP ({@code RIFF....WEBP}).
     * <p>
     * Kỳ vọng: {@code service.validate(...)} vượt qua không lỗi.
     */
    @Test
    void acceptsJpgJpegPngAndWebpSignatures() {
        // Kiểm tra từng chữ ký nhị phân chuẩn: JPEG (FF D8 FF), PNG (89 P N G \r \n 1A \n), WebP (RIFF + WEBP)
        assertDoesNotThrow(() -> service.validate(List.of(
                new MockMultipartFile("images", "a.jpeg", "image/jpeg",
                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}),
                new MockMultipartFile("images", "a.png", "image/png",
                        new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}),
                new MockMultipartFile("images", "b.webp", "image/webp", "RIFF0000WEBP".getBytes()))));
    }

    /**
     * Kiểm thử khi upload nhiều ảnh mà ảnh thứ 2 bị lỗi, service phải xóa ngay các ảnh đã upload thành công trong lần thử đó.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Upload 2 file; lần gọi 1 trả về {@code "new-url"}, lần gọi 2 ném {@link IllegalStateException}.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code IMAGE_UPLOAD_FAILED} và gọi {@code cloudinary.deleteImage("new-url")} để tránh ảnh mồ côi.
     */
    @Test
    void uploadFailureRemovesOnlyImagesUploadedInThisAttempt() {
        var file = jpeg();
        // Giả lập ảnh 1 lên Cloudinary thành công nhưng ảnh 2 bị lỗi mạng giữa chừng
        when(cloudinary.uploadImage(file, "fashion_website/returns"))
                .thenReturn("new-url").thenThrow(new IllegalStateException("upload failed"));
        assertCode(ReturnRequestErrorCode.IMAGE_UPLOAD_FAILED, () -> service.upload(List.of(file, file)));
        // Phải dọn ảnh thứ 1 vừa upload để không để lại file rác trên Cloudinary
        verify(cloudinary).deleteImage("new-url");
    }

    /**
     * Kiểm thử cơ chế đồng bộ Transaction: tự động xóa ảnh vừa upload nếu DB rollback, nhưng giữ nguyên ảnh nếu DB commit thành công.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Kích hoạt {@link TransactionSynchronizationManager}, upload 1 file thành công nhận {@code "new-url"},
     * lần lượt phát tín hiệu {@code STATUS_ROLLED_BACK} và {@code STATUS_COMMITTED}.
     * <p>
     * Kỳ vọng: Khi {@code STATUS_ROLLED_BACK} thì gọi {@code deleteImage("new-url")} đúng 1 lần;
     * khi {@code STATUS_COMMITTED} thì không xóa ảnh (0 lần).
     */
    @Test
    void rollsBackImagesOnDatabaseFailureButKeepsThemAfterCommit() {
        for (int completion : List.of(TransactionSynchronization.STATUS_ROLLED_BACK,
                TransactionSynchronization.STATUS_COMMITTED)) {
            reset(cloudinary);
            var file = jpeg();
            when(cloudinary.uploadImage(file, "fashion_website/returns")).thenReturn("new-url");
            TransactionSynchronizationManager.initSynchronization();
            try {
                assertEquals(List.of("new-url"), service.upload(List.of(file)));
                // Mô phỏng Spring Transaction gọi callback afterCompletion sau khi kết thúc giao dịch DB
                for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCompletion(completion);
                }
                // Chỉ xóa ảnh trên Cloudinary nếu transaction DB bị rollback
                verify(cloudinary, times(completion == TransactionSynchronization.STATUS_ROLLED_BACK ? 1 : 0))
                        .deleteImage("new-url");
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    /**
     * Kiểm thử xóa ảnh cũ khi khách hàng cập nhật thay thế ảnh: chỉ thực sự gọi Cloudinary xóa ảnh cũ sau khi transaction commit.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Bật {@link TransactionSynchronizationManager}, gọi {@code deleteOldImagesAfterCommit(["old-1", "old-2"])},
     * kiểm tra trước và sau khi phát {@code afterCommit()}.
     * <p>
     * Kỳ vọng: Trước {@code afterCommit()} không gọi {@code cloudinary}; sau {@code afterCommit()} mới gọi xóa cả {@code old-1} và {@code old-2}.
     */
    @Test
    void deletesOldImagesAfterCommitWhenReplaced() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteOldImagesAfterCommit(List.of("old-1", "old-2"));
            // Chưa commit thì chưa được xóa ảnh cũ, phòng trường hợp lưu DB thất bại khiến mất sạch cả ảnh cũ lẫn mới
            verifyNoInteractions(cloudinary);
            for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(cloudinary).deleteImage("old-1");
            verify(cloudinary).deleteImage("old-2");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * Hàm trợ giúp kiểm tra một hành động ném ra {@link AppException} với đúng mã lỗi {@link ReturnRequestErrorCode} mong muốn.
     *
     * @param code mã lỗi nghiệp vụ kỳ vọng
     * @param action đoạn mã cần thực thi để kiểm tra ngoại lệ
     */
    private void assertCode(ReturnRequestErrorCode code, Runnable action) {
        assertSame(code, assertThrows(AppException.class, action::run).getErrorCode());
    }
}
