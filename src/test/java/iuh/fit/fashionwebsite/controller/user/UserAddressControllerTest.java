package iuh.fit.fashionwebsite.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.fashionwebsite.config.SecurityConfig;
import iuh.fit.fashionwebsite.dto.request.user.UserAddressCreationRequest;
import iuh.fit.fashionwebsite.dto.request.user.UserAddressUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.user.UserAddressResponse;
import iuh.fit.fashionwebsite.enums.AddressType;
import iuh.fit.fashionwebsite.exception.GlobalExceptionHandler;
import iuh.fit.fashionwebsite.security.CustomJwtDecoder;
import iuh.fit.fashionwebsite.service.user.UserAddressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserAddressController.class,
        properties = "app.cors.allowed-origins=http://localhost")
@ContextConfiguration(classes = {UserAddressController.class, SecurityConfig.class, GlobalExceptionHandler.class})
class UserAddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserAddressService userAddressService;

    @MockitoBean
    private CustomJwtDecoder customJwtDecoder;

    private UserAddressResponse createMockResponse() {
        return UserAddressResponse.builder()
                .id(1L)
                .fullAddress("123 Nguyễn Huệ, Phường Bến Nghé, Quận 1")
                .provinceName("Hồ Chí Minh")
                .wardName("Phường Bến Nghé")
                .addressType(AddressType.HOME)
                .isDefault(true)
                .userId(10L)
                .build();
    }

    @Test
    @DisplayName("POST /users/me/addresses thành công trả về 201 Created")
    void createAddress_me_success() throws Exception {
        UserAddressCreationRequest request = UserAddressCreationRequest.builder()
                .fullAddress("123 Nguyễn Huệ")
                .provinceName("Hồ Chí Minh")
                .wardName("Phường Bến Nghé")
                .addressType(AddressType.HOME)
                .isDefault(true)
                .build();

        when(userAddressService.createAddress(any(UserAddressCreationRequest.class)))
                .thenReturn(createMockResponse());

        mockMvc.perform(post("/users/me/addresses")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.fullAddress").value("123 Nguyễn Huệ, Phường Bến Nghé, Quận 1"))
                .andExpect(jsonPath("$.data.provinceName").value("Hồ Chí Minh"))
                .andExpect(jsonPath("$.data.wardName").value("Phường Bến Nghé"));
    }

    @Test
    @DisplayName("POST /users/addresses (backward compatibility) thành công trả về 201 Created")
    void createAddress_legacyPath_success() throws Exception {
        UserAddressCreationRequest request = UserAddressCreationRequest.builder()
                .fullAddress("123 Nguyễn Huệ")
                .provinceName("Hồ Chí Minh")
                .wardName("Phường Bến Nghé")
                .addressType(AddressType.HOME)
                .build();

        when(userAddressService.createAddress(any(UserAddressCreationRequest.class)))
                .thenReturn(createMockResponse());

        mockMvc.perform(post("/users/addresses")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    @DisplayName("POST /users/me/addresses thiếu trường bắt buộc -> 400 Bad Request")
    void createAddress_validationFailure() throws Exception {
        UserAddressCreationRequest request = UserAddressCreationRequest.builder()
                .fullAddress("") // Blank
                .build();

        mockMvc.perform(post("/users/me/addresses")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /users/me/addresses trả về danh sách địa chỉ 200 OK")
    void getMyAddresses_success() throws Exception {
        when(userAddressService.getMyAddresses()).thenReturn(List.of(createMockResponse()));

        mockMvc.perform(get("/users/me/addresses")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1L));
    }

    @Test
    @DisplayName("GET /users/me/addresses/default trả về địa chỉ mặc định 200 OK")
    void getDefaultAddress_success() throws Exception {
        when(userAddressService.getDefaultAddress()).thenReturn(createMockResponse());

        mockMvc.perform(get("/users/me/addresses/default")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    @DisplayName("GET /users/me/addresses/{id} trả về chi tiết địa chỉ 200 OK")
    void getMyAddressById_success() throws Exception {
        when(userAddressService.getMyAddressById(1L)).thenReturn(createMockResponse());

        mockMvc.perform(get("/users/me/addresses/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    @DisplayName("PUT /users/me/addresses/{id} cập nhật địa chỉ 200 OK")
    void updateAddress_success() throws Exception {
        UserAddressUpdateRequest request = UserAddressUpdateRequest.builder()
                .fullAddress("456 Updated Street")
                .build();

        when(userAddressService.updateAddress(eq(1L), any(UserAddressUpdateRequest.class)))
                .thenReturn(createMockResponse());

        mockMvc.perform(put("/users/me/addresses/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /users/me/addresses/{id} xóa địa chỉ 200 OK")
    void deleteAddress_success() throws Exception {
        mockMvc.perform(delete("/users/me/addresses/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Address has been deleted successfully"));

        verify(userAddressService).deleteAddress(1L);
    }

    @Test
    @DisplayName("PATCH /users/me/addresses/{id}/default đặt địa chỉ mặc định 200 OK")
    void setDefaultAddress_success() throws Exception {
        when(userAddressService.setDefaultAddress(1L)).thenReturn(createMockResponse());

        mockMvc.perform(patch("/users/me/addresses/1/default")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    @DisplayName("GET /users/{userId}/addresses với ROLE_ADMIN thành công")
    void getUserAddressesByAdmin_asAdmin_success() throws Exception {
        when(userAddressService.getUserAddressesByAdmin(5L)).thenReturn(List.of(createMockResponse()));

        mockMvc.perform(get("/users/5/addresses")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1L));
    }

    @Test
    @DisplayName("GET /users/me/addresses trả về code 200 trong body")
    void getMyAddresses_returnsCode200() throws Exception {
        when(userAddressService.getMyAddresses()).thenReturn(List.of());

        mockMvc.perform(get("/users/me/addresses")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /users/me/addresses/{id} với fullAddress rỗng -> 400 Bad Request")
    void updateAddress_blankFullAddress_badRequest() throws Exception {
        mockMvc.perform(put("/users/me/addresses/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullAddress\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /users/{userId}/addresses/{addressId} với ROLE_STAFF thành công")
    void getUserAddressByIdByAdmin_asStaff_success() throws Exception {
        when(userAddressService.getUserAddressByIdByAdmin(5L, 1L)).thenReturn(createMockResponse());

        mockMvc.perform(get("/users/5/addresses/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    @DisplayName("GET /users/{userId}/addresses/{addressId} với ROLE_CUSTOMER -> 403")
    void getUserAddressByIdByAdmin_asCustomer_forbidden() throws Exception {
        mockMvc.perform(get("/users/5/addresses/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /users/{userId}/addresses với ROLE_CUSTOMER bị cấm 403 Forbidden theo role ở Controller")
    void getUserAddressesByAdmin_asCustomer_forbidden() throws Exception {
        mockMvc.perform(get("/users/5/addresses")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }
}
