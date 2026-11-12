package com.company.photogram.web;

import com.company.photogram.domain.AppUserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Эндпоинты профиля и списка пользователей (требуют JWT).
 */
@RestController
public class AppUserController {
    private final AppUserRepository appUserRepository;

    public AppUserController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * Возвращает логин текущего пользователя из JWT.
     *
     * @param jwt токен текущего запроса
     * @return {@code {"username": ...}}
     */
    @GetMapping("/me")
    public Map<String, String> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("username", jwt.getSubject());
    }

    /**
     * Возвращает всех пользователей.
     *
     * @return список пользователей
     */
    @GetMapping("/users")
    public Iterable<?> getUsers() {
        return appUserRepository.findAll();
    }
}
