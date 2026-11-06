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
    @DisplayName("")
    void likePhoto_addsLikeAndSaves() {

    }

}
