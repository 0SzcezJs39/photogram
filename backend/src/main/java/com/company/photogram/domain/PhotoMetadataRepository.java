package com.company.photogram.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface PhotoMetadataRepository extends JpaRepository<PhotoMetadata, Long> {
}