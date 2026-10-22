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

    // для REST
    public PhotoMetadata uploadPhoto(MultipartFile file, AppUser owner) throws IOException {
        return uploadPhoto(file, owner, List.of());
    }

    public PhotoMetadata uploadPhoto(MultipartFile file, AppUser owner, List<String> tags) throws IOException {
        return doUpload(file.getOriginalFilename(), file.getContentType(),
                file.getSize(), file.getBytes(), owner, tags);
    }

    // для файла с диска (для curl) (потом уберу)
    public PhotoMetadata uploadPhoto(Path file, AppUser owner) throws IOException {
        String contentType = Files.probeContentType(file);
//        if (contentType == null) contentType = "image/jpeg";
        return doUpload(file.getFileName().toString(), contentType,
                Files.size(file), Files.readAllBytes(file), owner, List.of());
    }

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

        // 3. Метаданные → MariaDB (fileName — оригинальное имя, s3Key — ключ в хранилище)
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

    // Отдаём содержимое объекта из S3 для проксирования через backend
    public PhotoContent loadContent(Long photoId) {
        PhotoMetadata photo = photoMetadataRepository.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото не найдено: " + photoId));

        byte[] bytes = s3Client.getObjectAsBytes(b -> b.bucket(bucketName).key(photo.getS3Key())).asByteArray();
        return new PhotoContent(photo.getContentType(), bytes);
    }

    public record PhotoContent(String contentType, byte[] bytes) {}

    // потому что метод делает несколько операций с БД как одно целое
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