package by.marketplace.auth;

import by.marketplace.auth.controller.AuthController;
import by.marketplace.auth.dto.AuthResponse;
import by.marketplace.auth.service.JwtService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void testSendOtp_validPhone_returns202() throws Exception {
        doNothing().when(otpService).sendOtp(any(), any());

        mockMvc.perform(post("/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"+375291234567\"}"))
            .andExpect(status().isAccepted());
    }

    @Test
    void testSendOtp_invalidPhone_returns400() throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"abc\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testVerifyOtp_validCode_returns200() throws Exception {
        AuthResponse response = new AuthResponse("access-token", "refresh-token", 900L);
        when(otpService.verifyOtp(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"+375291234567\",\"channel\":\"SMS\",\"otp\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void testVerifyOtp_invalidCode_returns400() throws Exception {
        doThrow(new AppException(ErrorCode.OTP_INVALID))
            .when(otpService).verifyOtp(any(), any(), any());

        mockMvc.perform(post("/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"+375291234567\",\"channel\":\"SMS\",\"otp\":\"000000\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testRefreshToken_validToken_returns200() throws Exception {
        AuthResponse response = new AuthResponse("new-access", "new-refresh", 900L);
        when(jwtService.rotateRefreshToken(any())).thenReturn(response);

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"valid-token\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void testRefreshToken_invalidToken_returns401() throws Exception {
        doThrow(new AppException(ErrorCode.INVALID_REFRESH_TOKEN))
            .when(jwtService).rotateRefreshToken(any());

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"invalid\"}"))
            .andExpect(status().isUnauthorized());
    }
}
