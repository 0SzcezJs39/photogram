package com.company.photogram.service;


import com.company.photogram.domain.AppUser;
import com.company.photogram.domain.PhotoMetadata;
import com.company.photogram.domain.PhotoMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PhotoServiceTest {
    @Mock
    private S3Client s3Client;

    @Mock
    private PhotoMetadataRepository repository;

    @InjectMocks
    private PhotoService photoService;

    private AppUser owner;
    private PhotoMetadata photo;

    @BeforeEach
    void setUp() {
        owner = new AppUser("BobTester", "passwdHash");
        owner.setId(1L);
        photo = new PhotoMetadata(
                "cat.jpg",
                "s3-key",
                "image/jpeg",
                777L,
                LocalDateTime.now(),
                List.of("cats"),
                owner
        );
        photo.setId(10L);
    }


    @Test
    @DisplayName("Добавление лайка и сохранение фото")
    void likePhoto_addsLikeAndSaves() {
        // Arrange — подготовка: настраиваем, как моки отвечают сервису
        when(repository.findById(10L)) // регистрируем заглушку для вызова findById(10L) на моке репозитория
                .thenReturn(Optional.of(photo)); // когда сервис попросит фото с id=10, вернуть Optional с нашим photo

        when(repository.save(any(PhotoMetadata.class))) // заглушка для save: принять любой объект PhotoMetadata
                .thenAnswer(invocation -> invocation.getArgument(0)); // вернуть тот же объект, который передали в save (имитация сохранения)

        // Act — сам вызов тестируемого метода
        PhotoMetadata result = photoService.likePhoto(10L, owner); // вызываем likePhoto для фото id=10 от пользователя owner и кладём результат в result

        // Assert — проверка результата
        assertEquals(1L, result.getLikeCount()); // ожидаем ровно 1 лайк у возвращённого фото
        assertTrue(result.getLikedUserIds().contains(1L)); // ожидаем, что в списке лайкнувших есть пользователь с id=1
        verify(repository).save(photo); // ожидаем, что сервис один раз вызвал save с нашим photo (побочный эффект)

        /*
        - assertEquals(ожидаемое, фактическое) - первое значение ты задаёшь сам, второе вернул код.
        - assertTrue(условие) - ожидание, что условие истинно.
        - verify(...) - ожидание побочного эффекта (сохранение в репозиторий).
        */
    }

    @Test
    @DisplayName("Повторный лайк не учитывается")
    void likePhoto_whenAlreadyLiked_doesNotSaveTwice() {
        // Arrange — настраиваем моки
        when(repository.findById(10L)) // при запросе фото с id=10
                .thenReturn(Optional.of(photo)); // возвращаем наше photo
        when(repository.save(any(PhotoMetadata.class))) // при любом вызове save
                .thenAnswer(invocation -> invocation.getArgument(0)); // возвращаем переданный объект

        // Act — вызываем лайк дважды от одного и того же пользователя
        photoService.likePhoto(10L, owner); // первый лайк: пользователь добавляется в likedUserIds
        PhotoMetadata result = photoService.likePhoto(10L, owner); // второй лайк: Set.add вернёт false

        // Assert — лайк должен остаться один, а save - вызваться только один раз
        assertEquals(1L, result.getLikeCount()); // счётчик лайков не увеличился
        assertTrue(result.getLikedUserIds().contains(1L)); // пользователь id=1 всё ещё в списке
        verify(repository, times(1)).save(photo); // save вызван ровно один раз, а не два
    }

}
