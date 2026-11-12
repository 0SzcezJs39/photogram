package com.company.photogram.service;

import com.company.photogram.domain.AppUser;
import com.company.photogram.domain.PhotoMetadata;
import com.company.photogram.domain.PhotoMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Бизнес-логика работы с фото: загрузка в S3, чтение байтов, лайки и теги.
 */
@Service
public class PhotoService {
    private final S3Client s3Client;
    private final PhotoMetadataRepository photoMetadataRepository;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public PhotoService(S3Client s3Client, PhotoMetadataRepository photoMetadataRepository) {
        this.s3Client = s3Client;
        this.photoMetadataRepository = photoMetadataRepository;
    }

    /**
     * Без тегов.
     */
    public PhotoMetadata uploadPhoto(MultipartFile file, AppUser owner) throws IOException {
        return uploadPhoto(file, owner, List.of());
    }

    /**
     * Загружает фото с тегами в S3 и сохраняет метаданные в БД.
     */
    public PhotoMetadata uploadPhoto(MultipartFile file, AppUser owner, List<String> tags) throws IOException {
        return doUpload(file.getOriginalFilename(), file.getContentType(),
                file.getSize(), file.getBytes(), owner, tags);
    }

    /**
     * Загружает фото из локального файла (удобно для отладки через curl).
     */
    public PhotoMetadata uploadPhoto(Path file, AppUser owner) throws IOException {
        String contentType = Files.probeContentType(file);
//        if (contentType == null) contentType = "image/jpeg";
        return doUpload(file.getFileName().toString(), contentType,
                Files.size(file), Files.readAllBytes(file), owner, List.of());
    }

    /**
     * Общая реализация загрузки: уникальный ключ, объект в S3, запись в БД.
     */
    private PhotoMetadata doUpload(String originalFilename, String contentType,
                                   long sizeBytes, byte[] bytes, AppUser owner, List<String> tags) {
        // 1. Уникальный ключ в S3, чтобы не перезаписывать совпадающие имена
        String s3Key = UUID.randomUUID() + originalFilename;

        // 2. Отправляем файл в S3 (Garage)
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .build();
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));

        // 3. Отправляем метаданные в MariaDB (fileName — оригинальное имя, s3Key — ключ в хранилище)
        PhotoMetadata metadata = new PhotoMetadata(
                originalFilename,
                s3Key,
                contentType,
                sizeBytes,
                LocalDateTime.now(),
                tags,
                owner
        );
        return photoMetadataRepository.save(metadata);
    }

    /**
     * Загружает байты фото из S3 для проксирования через backend.
     *
     * @param photoId идентификатор фото
     * @return MIME-тип и содержимое файла
     * @throws ResponseStatusException 404, если фото нет
     */
    public PhotoContent loadContent(Long photoId) {
        PhotoMetadata photo = photoMetadataRepository.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото не найдено: " + photoId));

        byte[] bytes = s3Client.getObjectAsBytes(b -> b.bucket(bucketName).key(photo.getS3Key())).asByteArray();
        return new PhotoContent(photo.getContentType(), bytes);
    }

    /**
     * Содержимое фото, готовое к отдаче HTTP-клиенту.
     */
    public record PhotoContent(String contentType, byte[] bytes) {}

    /**
     * Ставит лайк; повторный лайк игнорируется.
     *
     * @param photoId идентификатор фото
     * @param user    пользователь, ставящий лайк
     * @return актуальные метаданные фото
     * @throws ResponseStatusException 404, если фото нет
     */
    @Transactional
    public PhotoMetadata likePhoto(Long photoId, AppUser user) {
        PhotoMetadata photo = photoMetadataRepository.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото не найдено: " + photoId));

        // Set.add() вернёт false, если пользователь уже лайкнул это фото
        if (photo.getLikedUserIds().add(user.getId())) {
            return photoMetadataRepository.save(photo);
        }
        return photo;
    }

    /**
     * Убирает лайк; если лайка не было — ничего не меняет.
     *
     * @param photoId идентификатор фото
     * @param user    пользователь, убирающий лайк
     * @return актуальные метаданные фото
     * @throws ResponseStatusException 404, если фото нет
     */
    @Transactional
    public PhotoMetadata unlikePhoto(Long photoId, AppUser user) {
        PhotoMetadata photo = photoMetadataRepository.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото не найдено: " + photoId));

        // Set.remove() вернёт false, если лайка и не было
        if (photo.getLikedUserIds().remove(user.getId())) {
            return photoMetadataRepository.save(photo);
        }
        return photo;
    }

    /**
     * Заменяет теги фото; доступно только владельцу.
     *
     * @param photoId идентификатор фото
     * @param tags    новые теги
     * @param user    текущий пользователь
     * @return актуальные метаданные фото
     * @throws ResponseStatusException 404, если фото нет, 403 если не владелец
     */
    @Transactional
    public PhotoMetadata updateTags(Long photoId, List<String> tags, AppUser user) {
        PhotoMetadata photo = photoMetadataRepository.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото не найдено: " + photoId));

        if (!photo.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Можно менять теги только своих фотографий");
        }

        photo.setTags(tags);
        return photoMetadataRepository.save(photo);
    }
}