package com.company.photogram;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;


/**
 * Настройки безопасности: stateless-режим, правила доступа и HS256 JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    /**
     * Определяет публичные эндпоинты и требует JWT для всех остальных.
     *
     * @param http конфигуратор цепочки фильтров Spring Security
     * @return собранная цепочка фильтров
     * @throws Exception при ошибке конфигурации
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())                                       // REST: CSRF-токен не нужен
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // без серверной сессии
                .requestCache(rc -> rc.disable())                                  // не сохранять запросы в сессию
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/photos").permitAll()      // лента доступна всем
                        .requestMatchers(HttpMethod.GET, "/photos/*/image").permitAll() // картинки ленты доступны всем
                        .requestMatchers("/auth/login", "/auth/register").permitAll() // вход/регистрация без токена
                        .anyRequest().authenticated())                              // остальное - только по JWT
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())) // проверка Bearer-токена
                .build();
    }

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    /**
     * Превращает секрет из конфигурации в ключ HMAC-SHA256.
     *
     * @return ключ для подписи и проверки JWT
     */
    private SecretKey jwtSecretKey() {
        return new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * Декодер для проверки входящих Bearer-токенов.
     *
     * @return декодер JWT на симметричном ключе
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Кодировщик для выпуска JWT при логине.
     *
     * @return кодировщик JWT на симметричном ключе
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey()));
    }

    /**
     * Собирает менеджер аутентификации, проверяющий логин/пароль по БД.
     *
     * @param userDetailsService загрузчик пользователей из БД
     * @param passwordEncoder    кодировщик паролей
     * @return менеджер аутентификации
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * Возвращает BCrypt-кодировщик паролей.
     *
     * @return кодировщик паролей
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
