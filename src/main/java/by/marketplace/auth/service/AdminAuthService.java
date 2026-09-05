package by.marketplace.auth.service;

import by.marketplace.auth.dto.*;

import java.util.UUID;

public interface AdminAuthService {

    AdminAuthResponse login(AdminLoginRequest req);
    AdminTotpSetupResponse setupTotp(AdminSetupTotpRequest req);
    AdminDto getCurrentAdmin(UUID adminId);
}
