package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<Media, Integer> {

    Optional<Media> findByOwnerIdAndIdempotencyKey(Integer ownerId, String idempotencyKey);

    List<Media> findByOwnerId(Integer ownerId);

    List<Media> findByOwnerIdAndCategory(Integer ownerId, MediaCategory category);

    Page<Media> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    List<Media> findByVisibilityAndStorageKeyNotStartingWith(MediaVisibility visibility, String prefix);
}
