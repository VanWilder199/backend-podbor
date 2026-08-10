package by.marketplace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;


public record SendOtpRequest(
    @Email
    String email,
    @Pattern(regexp="^\\+375\\d{9}$")
    String phoneNumber
) {
    public String destination() {
        return phoneNumber != null ? phoneNumber : email;
    }

    public Channel channel() {
        return  phoneNumber != null ? Channel.SMS : Channel.EMAIL;
    }
}
