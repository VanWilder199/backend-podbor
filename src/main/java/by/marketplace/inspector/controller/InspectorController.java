package by.marketplace.inspector.controller;

import by.marketplace.inspector.TelegramUser;
import by.marketplace.inspector.dto.InspectorDto;
import by.marketplace.inspector.dto.RegisterInspectorRequest;
import by.marketplace.inspector.service.InspectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/inspector")
@RequiredArgsConstructor
public class InspectorController {

    private final InspectorService inspectorService;


    @PostMapping("/register")
    ResponseEntity<InspectorDto> register(
            @AuthenticationPrincipal TelegramUser telegramUser,
            @Valid @RequestBody RegisterInspectorRequest request
            ) {
        return ResponseEntity.ok(inspectorService.register(telegramUser, request));
    }

    @GetMapping("/")
    ResponseEntity<Optional<InspectorDto>> inspector(
            @AuthenticationPrincipal TelegramUser telegramUser
    ) {
        return ResponseEntity.ok(inspectorService.findByTelegramId(telegramUser.id()));
    }


}
