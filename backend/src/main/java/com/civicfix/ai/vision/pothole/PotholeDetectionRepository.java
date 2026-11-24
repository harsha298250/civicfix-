package com.civicfix.ai.vision.pothole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PotholeDetectionRepository extends JpaRepository<PotholeDetection, UUID> {

    List<PotholeDetection> findByComplaintIdOrderByCreatedAtDesc(UUID complaintId);

    Optional<PotholeDetection> findFirstByComplaintIdOrderByCreatedAtDesc(UUID complaintId);

    Optional<PotholeDetection> findFirstByComplaintIdAndPhotoUrl(UUID complaintId, String photoUrl);
}
