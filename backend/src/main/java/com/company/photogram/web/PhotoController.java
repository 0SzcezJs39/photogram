package com.company.photogram.web;

import com.company.photogram.domain.AppUser;
import com.company.photogram.domain.AppUserRepository;
import com.company.photogram.domain.PhotoMetadata;
import com.company.photogram.domain.PhotoMetadataRepository;
import com.company.photogram.service.PhotoService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

/**
 * HTTP-эндпоинты ленты: список, картинки, загрузка, лайки и теги.
 */
@RestController
public class PhotoController {
    private final PhotoMetadataRepository repository;
    private final PhotoService photoService;
    private final AppUserRepository appUserRepository;

    public PhotoController(PhotoMetadataRepository repository, PhotoService photoService, AppUserRepository appUserRepository) {
        this.repository = repository;
        this.photoService = photoService;
        this.appUserRepository = appUserRepository;
    }

    /**
     * DTO ответа: скрывает список лайкнувших и добавляет URL картинки и флаг {@code liked}.
     */
    public record PhotoResponse(
            Long id,
            String fileName,
            String contentType,
            String imageUrl,
            Long sizeBytes,
            LocalDateTime createdAt,
            List<String> tags,
            String owner,
            long likeCount,
            boolean liked
    ) {
        /**
         * Собирает DTO из сущности с учётом текущего пользователя.
         *
         * @param p             метаданные фото
         * @param currentUserId id текущего пользователя или {@code null}
         * @return ответ для клиента
         */
        static PhotoResponse of(PhotoMetadata p, Long currentUserId) {
            boolean liked = currentUserId != null && p.getLikedUserIds().contains(currentUserId);
            return new PhotoResponse(
                    p.getId(), p.getFileName(), p.getContentType(), "/api/photos/" + p.getId() + "/image",
                    p.getSizeBytes(), p.getCreatedAt(), p.getTags(),
                    p.getOwner() != null ? p.getOwner().getUsername() : null,
                    p.getLikeCount(), liked);
        }
    }

    /**
     * Публично отдаёт байты картинки, проксируя их из S3.
     *
     * @param id идентификатор фото
     * @return содержимое файла с его MIME-типом
     */
    @GetMapping("/photos/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        PhotoService.PhotoContent content = photoService.loadContent(id);
        MediaType mediaType = content.contentType() != null
                ? MediaType.parseMediaType(content.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(mediaType).body(content.bytes());
    }

    /**
     * Отладочная заглушка для проверки доступности API.
     *
     * @return тестовая строка
     */
    @GetMapping("/test")
    public String getTest() { return "1243"; }

    /**
     * Публичная лента фото с сортировкой по дате и необязательным фильтром по тегу.
     *
     * @param tag тег (ведущий {@code #} отбрасывается)
     * @param jwt токен текущего запроса или {@code null} для анонимного доступа
     * @return список фото; без JWT флаг {@code liked} всегда {@code false}
     */
    @GetMapping("/photos")
    public List<PhotoResponse> getPhotos(
            @RequestParam(value = "tag", required = false) String tag,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long currentUserId = currentUserIdOrNull(jwt);
        String query = normalizeTag(tag);
        List<PhotoMetadata> photos = repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        // Пока поиск только по тегам: фильтруем по точному совпадению тега без учёта регистра
        if (!query.isBlank()) {
            photos = photos.stream()
                    .filter(p -> p.getTags() != null
                            && p.getTags().stream().anyMatch(t -> t != null && t.equalsIgnoreCase(query)))
                    .toList();
        }
        return photos.stream()
                .map(p -> PhotoResponse.of(p, currentUserId))
                .toList();
    }

    /**
     * Убирает ведущие {@code #} и пробелы у тега.
     *
     * @param tag исходный тег
     * @return нормализованный тег или пустая строка
     */
    private String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().replaceFirst("^#+", "").trim();
    }

    /**
     * Принимает multipart-файл с необязательными тегами и загружает его.
     *
     * @param file файл из формы
     * @param tags теги (можно несколько полей {@code tags})
     * @param jwt  токен текущего пользователя
     * @return метаданные загруженного фото
     * @throws IOException при чтении файла
     */
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED) // Указываем код 201 через аннотацию
    public PhotoResponse uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @AuthenticationPrincipal Jwt jwt
    ) throws IOException {
        AppUser owner = currentUser(jwt);
        return PhotoResponse.of(photoService.uploadPhoto(file, owner, normalizeTags(tags)), owner.getId());
    }

    /**
     * Возвращает логины пользователей, лайкнувших фото (только с JWT).
     *
     * @param id идентификатор фото
     * @return список логинов
     */
    @GetMapping("/photos/{id}/likes")
    public List<String> getLikers(@PathVariable Long id) {
        PhotoMetadata photo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото не найдено: " + id));
        return StreamSupport.stream(appUserRepository.findAllById(photo.getLikedUserIds()).spliterator(), false)
                .map(AppUser::getUsername)
                .toList();
    }

    /**
     * Ставит лайк текущим пользователем.
     *
     * @param id  идентификатор фото
     * @param jwt токен текущего пользователя
     * @return обновлённое фото
     */
    @PostMapping("/photos/{id}/like")
    public PhotoResponse likePhoto(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AppUser liker = currentUser(jwt);
        return PhotoResponse.of(photoService.likePhoto(id, liker), liker.getId());
    }

    /**
     * Убирает лайк текущего пользователя.
     *
     * @param id  идентификатор фото
     * @param jwt токен текущего пользователя
     * @return обновлённое фото
     */
    @DeleteMapping("/photos/{id}/like")
    public PhotoResponse unlikePhoto(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AppUser liker = currentUser(jwt);
        return PhotoResponse.of(photoService.unlikePhoto(id, liker), liker.getId());
    }

    /**
     * Полностью заменяет теги фото; разрешено только владельцу.
     *
     * @param id   идентификатор фото
     * @param tags новые теги
     * @param jwt  токен текущего пользователя
     * @return обновлённое фото
     */
    @PutMapping("/photos/{id}/tags")
    public PhotoResponse updateTags(
            @PathVariable Long id,
            @RequestBody List<String> tags,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AppUser owner = appUserRepository.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));
        return PhotoResponse.of(photoService.updateTags(id, tags, owner), owner.getId());
    }

    /**
     * Находит пользователя по JWT, при отсутствии создаёт запись в БД.
     *
     * @param jwt токен текущего запроса
     * @return пользователь
     */
    private AppUser currentUser(Jwt jwt) {
        String username = jwt.getSubject();
        return appUserRepository.findByUsername(username)
                .orElseGet(() -> appUserRepository.save(new AppUser(username, UUID.randomUUID().toString())));
    }

    /**
     * Возвращает id текущего пользователя или {@code null} для анонимного запроса.
     *
     * @param jwt токен текущего запроса
     * @return id пользователя или {@code null}
     */
    private Long currentUserIdOrNull(Jwt jwt) {
        if (jwt == null) return null;
        return appUserRepository.findByUsername(jwt.getSubject()).map(AppUser::getId).orElse(null);
    }

    /**
     * Нормализует теги: убирает {@code #} и пробелы, отбрасывает пустые и дубликаты.
     *
     * @param tags исходные теги
     * @return очищенный список тегов
     */
    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) return List.of();
        return tags.stream()
                .map(t -> t == null ? "" : t.trim().replaceFirst("^#+", "").trim())
                .filter(t -> !t.isBlank())
                .distinct()
                .toList();
    }
}
