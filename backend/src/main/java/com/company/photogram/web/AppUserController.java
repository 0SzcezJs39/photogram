package com.company.photogram.web;

import com.company.photogram.domain.AppUserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AppUserController {
    private final AppUserRepository appUserRepository;

    public AppUserController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/me")
    public Map<String, String> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("username", jwt.getSubject());
    }

    @GetMapping("/users")
    public Iterable<?> getUsers() {
        return appUserRepository.findAll();
    }
}
