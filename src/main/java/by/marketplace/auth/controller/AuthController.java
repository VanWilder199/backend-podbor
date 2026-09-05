package by.marketplace.auth.controller;


import by.marketplace.auth.service.JwtService;
import by.marketplace.auth.OtpService;
import by.marketplace.auth.dto.AuthResponse;
import by.marketplace.auth.dto.RefreshRequest;
import by.marketplace.auth.dto.SendOtpRequest;
import by.marketplace.auth.dto.VerifyOtpRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OtpService otpService;
    private final JwtService jwtService;


    @PostMapping("/otp/send")
    public ResponseEntity<Void> sendOtp(@Valid @RequestBody SendOtpRequest req) {
        otpService.sendOtp(req.channel(), req.destination());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return ResponseEntity.ok(otpService.verifyOtp(req.channel(), req.destination(), req.otp()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(jwtService.rotateRefreshToken(req.refreshToken()));
    }

}
