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

@ExtendWith(MockitoExtension.class)
class ReturnRequestImageServiceTest {
    @Mock private CloudinaryService cloudinary;
    private ReturnRequestImageService service;

    @BeforeEach
    void setUp() {
        service = new ReturnRequestImageService(cloudinary);
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("images", "evidence.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0});
    }

    @Test
    void acceptsFiveFilesAndExactFiveMiBBoundary() {
        byte[] bytes = new byte[5 * 1024 * 1024];
        bytes[0] = (byte) 0xff; bytes[1] = (byte) 0xd8; bytes[2] = (byte) 0xff;
        var file = new MockMultipartFile("images", "a.jpg", "image/jpeg", bytes);
        assertDoesNotThrow(() -> service.validate(Collections.nCopies(5, file)));
    }

    @Test
    void rejectsSixFilesAndOversizedOrEmptyFilesWithoutUploading() {
        assertCode(ReturnRequestErrorCode.TOO_MANY_IMAGES, () -> service.upload(Collections.nCopies(6, jpeg())));
        assertCode(ReturnRequestErrorCode.INVALID_IMAGE, () -> service.upload(List.of(
                new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]))));
        assertCode(ReturnRequestErrorCode.INVALID_IMAGE, () -> service.upload(List.of(
                new MockMultipartFile("images", new byte[0]))));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void rejectsSpoofedMimeSvgInvalidExtensionAndWrongSignature() {
        for (String mime : List.of("image/jpeg", "image/png", "image/webp", "image/svg+xml")) {
            assertCode(ReturnRequestErrorCode.INVALID_IMAGE, () -> service.upload(List.of(
                    new MockMultipartFile("images", "fake.jpg", mime, "<svg/>".getBytes()))));
        }
        assertCode(ReturnRequestErrorCode.INVALID_IMAGE, () -> service.upload(List.of(
                new MockMultipartFile("images", "bad.gif", "image/jpeg",
                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}))));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void acceptsJpgJpegPngAndWebpSignatures() {
        assertDoesNotThrow(() -> service.validate(List.of(
                new MockMultipartFile("images", "a.jpeg", "image/jpeg",
                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}),
                new MockMultipartFile("images", "a.png", "image/png",
                        new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}),
                new MockMultipartFile("images", "b.webp", "image/webp", "RIFF0000WEBP".getBytes()))));
    }

    @Test
    void uploadFailureRemovesOnlyImagesUploadedInThisAttempt() {
        var file = jpeg();
        when(cloudinary.uploadImage(file, "fashion_website/returns"))
                .thenReturn("new-url").thenThrow(new IllegalStateException("upload failed"));
        assertCode(ReturnRequestErrorCode.IMAGE_UPLOAD_FAILED, () -> service.upload(List.of(file, file)));
        verify(cloudinary).deleteImage("new-url");
    }

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
                for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCompletion(completion);
                }
                verify(cloudinary, times(completion == TransactionSynchronization.STATUS_ROLLED_BACK ? 1 : 0))
                        .deleteImage("new-url");
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    void deletesOldImagesAfterCommitWhenReplaced() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteOldImagesAfterCommit(List.of("old-1", "old-2"));
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

    private void assertCode(ReturnRequestErrorCode code, Runnable action) {
        assertSame(code, assertThrows(AppException.class, action::run).getErrorCode());
    }
}
