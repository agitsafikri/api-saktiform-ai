package com.saktiform.api.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.MonthDay;
import java.time.Year;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.prefix}")
    private String prefix;

    @Value("${minio.public-url}")
    private String publicBaseUrl;

    public String upload(MultipartFile file) {
        try {
            String objectName = buildObjectName(file.getOriginalFilename());

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return objectName; // SIMPAN KE DB
        } catch (Exception e) {
            throw new RuntimeException("Upload failed", e);
        }
    }

    public String upload(InputStream inputStream,
                         String objectPath,
                         String contentType) {

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectPath)
                            .stream(inputStream, -1, 10 * 1024 * 1024)
                            .contentType(contentType)
                            .build()
            );

            return objectPath;
        } catch (Exception e) {
            throw new RuntimeException("Failed upload to MinIO", e);
        }
    }

    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate URL", e);
        }
    }

    private String buildObjectName(String originalFilename) {
        String ext = Optional.ofNullable(originalFilename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(f.lastIndexOf(".")))
                .orElse("");

        return prefix + "/"
                + Year.now() + "/"
                + MonthDay.now().getMonthValue() + "/"
                + UUID.randomUUID() + ext;
    }

    public String getPublicUrl(String path) {
        return publicBaseUrl + "/" + bucket + "/" + path;
    }

    public String extractPathFromPublicUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }

        String prefix = normalize(publicBaseUrl)
                + "/" + bucket + "/";

        if (!publicUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("URL tidak valid untuk bucket ini");
        }

        return publicUrl.substring(prefix.length());
    }

    private String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

}

