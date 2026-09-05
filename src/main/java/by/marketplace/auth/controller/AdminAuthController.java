package by.marketplace.auth.controller;

import by.marketplace.auth.dto.AdminAuthResponse;
import by.marketplace.auth.dto.AdminLoginRequest;
import by.marketplace.auth.dto.AdminSetupTotpRequest;
import by.marketplace.auth.dto.AdminTotpSetupResponse;
import by.marketplace.auth.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {
    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminAuthResponse> login(@Valid @RequestBody AdminLoginRequest req) {
        return ResponseEntity.ok(adminAuthService.login(req));
    }

    @PostMapping("/setup-totp")
    public ResponseEntity<AdminTotpSetupResponse> setupTotp(@Valid @RequestBody AdminSetupTotpRequest req) {
        return ResponseEntity.ok(adminAuthService.setupTotp(req));
    }


}
