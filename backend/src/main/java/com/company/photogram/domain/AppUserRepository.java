package com.company.photogram.domain;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

/**
 * Репозиторий пользователей. Spring Data генерирует реализацию автоматически.
 * REST-экспорт отключён, чтобы не выставлять сущность наружу.
 */
@RepositoryRestResource(exported = false)
public interface AppUserRepository extends CrudRepository<AppUser, Long> {
    /**
     * Ищет пользователя по логину.
     *
     * @param username логин
     * @return пользователь, если найден
     */
    Optional<AppUser> findByUsername(String username);
}
