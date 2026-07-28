package by.marketplace.auth.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank
        String destination,
        @NotBlank
                @Size(min = 6, max = 6)
        String otp
) {
}
