package com.company.photogram.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
public class PhotoMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String fileName, s3Key, contentType;
    private LocalDateTime createdAt;
    private Long sizeBytes;
    private List<String> tags;

    @ElementCollection
    @CollectionTable(name = "photo_liked_users", joinColumns = @JoinColumn(name = "photo_id"))
    @Column(name = "user_id")
    private Set<Long> likedUserIds = new HashSet<>();

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="owner")
    private AppUser owner;

    
    // Если убрать приложение не запускается
    public PhotoMetadata() { this.createdAt = LocalDateTime.now(); }
    
    public PhotoMetadata(String fileName, String s3Key, String contentType, Long sizeBytes, LocalDateTime createdAt, List<String> tags, AppUser owner) {
        this.fileName = fileName;
        this.s3Key = s3Key;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
        this.tags = tags;
        this.owner = owner;
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public String getS3Key() { return s3Key; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<String> getTags() { return tags; }
    public AppUser getOwner() { return owner; }
    public Set<Long> getLikedUserIds() { return likedUserIds; }
    public Long getLikeCount() { return (long) likedUserIds.size(); }

    public void setId(Long id) { this.id = id; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public void setOwner(AppUser owner) { this.owner = owner; }
}
