package com.company.photogram.web;

import com.company.photogram.domain.AppUser;
import com.company.photogram.domain.AppUserRepository;
import com.company.photogram.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Регистрация и вход; при успешном входе выдаёт JWT.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          TokenService tokenService,
                          AppUserRepository appUserRepository,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Тело запроса для логина и регистрации.
     */
    public record Credentials(String username, String password) {}

    /**
     * Проверяет логин/пароль и выдаёт JWT.
     *
     * @param creds учётные данные
     * @return токен и логин либо ошибку 400/401
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Credentials creds) {
        if (creds.username() == null || creds.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Укажите логин и пароль"));
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(creds.username(), creds.password()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Неверный логин или пароль"));
        }
        String token = tokenService.issueToken(creds.username());
        return ResponseEntity.ok(Map.of("token", token, "username", creds.username()));
    }

    /**
     * Создаёт пользователя с BCrypt-хэшем пароля.
     *
     * @param creds учётные данные
     * @return логин созданного пользователя либо ошибку 400/409
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Credentials creds) {
        if (creds.username() == null || creds.username().isBlank()
                || creds.password() == null || creds.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Укажите логин и пароль"));
        }
        if (appUserRepository.findByUsername(creds.username()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Пользователь уже существует"));
        }
        AppUser user = appUserRepository.save(
                new AppUser(creds.username(), passwordEncoder.encode(creds.password())));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("username", user.getUsername()));
    }
}
