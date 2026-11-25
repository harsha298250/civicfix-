package com.civicfix.complaint.dto;

public record PresignedUrlResponse(
        String photoId,
        String objectKey,
        String presignedUrl,
        int expiresInSeconds
) {}
