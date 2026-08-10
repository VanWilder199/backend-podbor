package by.marketplace;

import by.marketplace.auth.AuthController;
import by.marketplace.auth.JwtService;
import by.marketplace.auth.OtpService;
import by.marketplace.shared.exception.AppException;
import by.marketplace.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControlletTest {

    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private JwtService jwtService;

    @Autowired
    private  MockMvc mockMvc;


    @Test
    public void testSendOtp_invalidPhone_returns400() throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"+abc\"}")
        ).andExpect(status().isBadRequest());
    }

    @Test
    public void testSendOtp_validRequest_return202() throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\": \"+375291234567\"}")
        ).andExpect(status().isAccepted());
    }

    @Test
    public void testVerifyOtp_invalidCode_returns400() throws Exception {
             doThrow(new AppException(ErrorCode.OTP_INVALID))
                 .when(otpService).verifyOtp(any(), any(), any());

             mockMvc.perform(post("/auth/otp/verify")
                             .contentType(MediaType.APPLICATION_JSON)
                             .content("{\"destination\":\"+375291234567\",\"channel\":\"SMS\",\"otp\":\"000000\"}"))
                 .andExpect(status().isBadRequest());  // 400
         }

}
