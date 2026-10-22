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

    // Ответ клиенту: без внутреннего списка id лайкнувших, но с флагом liked для текущего пользователя
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
        static PhotoResponse of(PhotoMetadata p, Long currentUserId) {
            boolean liked = currentUserId != null && p.getLikedUserIds().contains(currentUserId);
            return new PhotoResponse(
                    p.getId(), p.getFileName(), p.getContentType(), "/api/photos/" + p.getId() + "/image",
                    p.getSizeBytes(), p.getCreatedAt(), p.getTags(),
                    p.getOwner() != null ? p.getOwner().getUsername() : null,
                    p.getLikeCount(), liked);
        }
    }

    // Публичная отдача байтов картинки через backend (проксирует S3/Garage, скрывая приватный ключ)
    @GetMapping("/photos/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        PhotoService.PhotoContent content = photoService.loadContent(id);
        MediaType mediaType = content.contentType() != null
                ? MediaType.parseMediaType(content.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(mediaType).body(content.bytes());
    }

    @GetMapping("/test")
    public String getTest() { return "1243"; }

    // Лента публичная: если JWT нет (jwt == null) — просто liked=false
    // Необязательный фильтр по тегу: /photos?tag=#природа (ведущий '#' отбрасывается)
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

    // Убираем ведущий '#', пробелы и регистр не учитываем при поиске
    private String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().replaceFirst("^#+", "").trim();
    }

    // @RequestParam("file") MultipartFile file — перехватывает файл из Multipart form-data
    // tags — необязательные хэштеги: можно передать несколько полей "tags" в форме
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

    // Кто поставил лайк: доступно только авторизованным (любой эндпоинт кроме GET /photos требует JWT)
    @GetMapping("/photos/{id}/likes")
    public List<String> getLikers(@PathVariable Long id) {
        PhotoMetadata photo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото не найдено: " + id));
        return StreamSupport.stream(appUserRepository.findAllById(photo.getLikedUserIds()).spliterator(), false)
                .map(AppUser::getUsername)
                .toList();
    }

    @PostMapping("/photos/{id}/like")
    public PhotoResponse likePhoto(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AppUser liker = currentUser(jwt);
        return PhotoResponse.of(photoService.likePhoto(id, liker), liker.getId());
    }

    @DeleteMapping("/photos/{id}/like")
    public PhotoResponse unlikePhoto(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AppUser liker = currentUser(jwt);
        return PhotoResponse.of(photoService.unlikePhoto(id, liker), liker.getId());
    }

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

    // Текущий пользователь из БД, при необходимости создаём запись (как было при загрузке/лайке)
    private AppUser currentUser(Jwt jwt) {
        String username = jwt.getSubject();
        return appUserRepository.findByUsername(username)
                .orElseGet(() -> appUserRepository.save(new AppUser(username, UUID.randomUUID().toString())));
    }

    private Long currentUserIdOrNull(Jwt jwt) {
        if (jwt == null) return null;
        return appUserRepository.findByUsername(jwt.getSubject()).map(AppUser::getId).orElse(null);
    }

    // Убираем ведущий '#', пробелы, пустые значения и дубликаты (без учёта регистра)
    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) return List.of();
        return tags.stream()
                .map(t -> t == null ? "" : t.trim().replaceFirst("^#+", "").trim())
                .filter(t -> !t.isBlank())
                .distinct()
                .toList();
    }
}
