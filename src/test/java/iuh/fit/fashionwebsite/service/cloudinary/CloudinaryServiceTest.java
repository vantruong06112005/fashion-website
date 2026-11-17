package iuh.fit.fashionwebsite.service.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinaryService = new CloudinaryService(cloudinary);
        ReflectionTestUtils.setField(cloudinaryService, "cloudName", "n8agmdkc");
    }

    @Test
    void testExtractPublicId_VariousFormats() {
        // Standard URL with version
        String url1 = "https://res.cloudinary.com/n8agmdkc/image/upload/v1792905150/fashion_website/avatars/abc12345.jpg";
        assertEquals("fashion_website/avatars/abc12345", cloudinaryService.extractPublicId(url1));

        // URL without version
        String url2 = "https://res.cloudinary.com/n8agmdkc/image/upload/fashion_website/avatars/abc12345.png";
        assertEquals("fashion_website/avatars/abc12345", cloudinaryService.extractPublicId(url2));

        // URL with transformation and version
        String url3 = "http://res.cloudinary.com/demo/image/upload/c_fill,h_100,w_100/v1234567/sample.jpg";
        assertEquals("sample", cloudinaryService.extractPublicId(url3));

        // URL with query params and fragment
        String url4 = "https://res.cloudinary.com/n8agmdkc/image/upload/v1792905150/fashion_website/avatars/user-1.jpg?t=123456#main";
        assertEquals("fashion_website/avatars/user-1", cloudinaryService.extractPublicId(url4));

        // Direct public ID
        String directId = "fashion_website/avatars/direct_id.jpg";
        assertEquals("fashion_website/avatars/direct_id", cloudinaryService.extractPublicId(directId));

        // Non-Cloudinary URL (e.g., Google user avatar)
        String googleUrl = "https://lh3.googleusercontent.com/a/ACg8ocL812345";
        assertNull(cloudinaryService.extractPublicId(googleUrl));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeleteImage_SuccessWithInvalidateOption() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("fashion_website/avatars/old_avatar"), anyMap()))
                .thenReturn(Map.of("result", "ok"));

        String oldUrl = "https://res.cloudinary.com/n8agmdkc/image/upload/v1792905150/fashion_website/avatars/old_avatar.jpg";
        cloudinaryService.deleteImage(oldUrl);

        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).destroy(eq("fashion_website/avatars/old_avatar"), mapCaptor.capture());

        Map captured = mapCaptor.getValue();
        assertEquals(Boolean.TRUE, captured.get("invalidate"));
        assertEquals("image", captured.get("resource_type"));
    }

    @Test
    void testDeleteImage_PreservesDemoSeedAssets() {
        String demoUrl = "https://res.cloudinary.com/n8agmdkc/image/upload/v1792905150/fashion_website/demo/fashion-v1/products/p001.jpg";
        cloudinaryService.deleteImage(demoUrl);

        verifyNoInteractions(uploader);
    }

    @Test
    void testDeleteImage_SkipsExternalUrls() {
        String googleUrl = "https://lh3.googleusercontent.com/a/ACg8ocL812345";
        cloudinaryService.deleteImage(googleUrl);

        verifyNoInteractions(uploader);
    }

    @Test
    void testDeleteImage_NullOrBlankIgnored() {
        cloudinaryService.deleteImage(null);
        cloudinaryService.deleteImage("");
        cloudinaryService.deleteImage("   ");

        verifyNoInteractions(uploader);
    }
}
