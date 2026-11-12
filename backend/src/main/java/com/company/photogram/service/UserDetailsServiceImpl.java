package com.company.photogram.service;

import com.company.photogram.domain.AppUser;
import com.company.photogram.domain.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Загружает пользователя из БД для проверки логина/пароля Spring Security.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    private final AppUserRepository repo;

    public UserDetailsServiceImpl(AppUserRepository repo) {
        this.repo = repo;
    }

    /**
     * Ищет пользователя по логину.
     *
     * @param username логин
     * @return данные пользователя для Spring Security
     * @throws UsernameNotFoundException если пользователь не найден
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<AppUser> user = repo.findByUsername(username);
        User.UserBuilder builder = null;
        if (user.isPresent()) {
            AppUser currUser = user.get();
            builder = org.springframework.security.core.userdetails.User.withUsername(username);
            builder.password(currUser.getPassword());
        } else {
            throw new UsernameNotFoundException("User not found!");
        }
        return builder.build();
    }
}
