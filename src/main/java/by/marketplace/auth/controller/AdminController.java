package by.marketplace.auth.controller;

import by.marketplace.auth.dto.AdminDto;
import by.marketplace.auth.service.AdminAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminAuthService adminAuthService;

    public AdminController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @GetMapping("/")
    public ResponseEntity<AdminDto> getAdmin(@AuthenticationPrincipal UUID adminId) {
        return ResponseEntity.ok(adminAuthService.getCurrentAdmin(adminId));
    }
}
