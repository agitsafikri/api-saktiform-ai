package com.saktiform.api.service;

import com.saktiform.api.model.product.ImageType;
import com.saktiform.api.util.ImageUtils;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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

    @Value("${minio.bucket.produk}")
    private String bucketProduk;

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
    public String getProdukPublicUrl(String path) {
        return publicBaseUrl + "/" + bucketProduk + "/" + path;
    }

    public String extractPathFromPublicUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }

        String prefix = normalize(publicBaseUrl);

        if(publicUrl.contains(bucket)){
            prefix = prefix + "/" + bucket + "/";
        }else if(publicUrl.contains(bucketProduk)){
            prefix = prefix + "/" + bucketProduk + "/";
        }

        if (!publicUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("URL tidak valid untuk bucket ini");
        }

        return publicUrl.substring(prefix.length());
    }

    private String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String copySameFolder(String sourcePath) throws Exception {

        // 1️⃣ Extract folder
        int lastSlash = sourcePath.lastIndexOf("/");
        String folder = sourcePath.substring(0, lastSlash + 1);
        // media/2026/2/

        // 2️⃣ Extract extension
        String extension = sourcePath.substring(sourcePath.lastIndexOf("."));
        // .png

        // 3️⃣ Generate new filename
        String newFileName = UUID.randomUUID() + extension;

        String destinationPath = folder + newFileName;

        // 4️⃣ Copy
        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .source(
                                CopySource.builder()
                                        .bucket(bucketProduk)
                                        .object(sourcePath)
                                        .build()
                        )
                        .bucket(bucketProduk)
                        .object(destinationPath)
                        .build()
        );

        return destinationPath;
    }


    public String uploadImage(MultipartFile file) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {

        ImageType type = ImageUtils.detectImageType(file);

        switch (type) {

            case SVG:
                uploadOriginal(file);
            case WEBP:
                return uploadOriginal(file);

            case RASTER:
                return uploadConverted(file);

            default:
                throw new IllegalArgumentException("File bukan gambar valid");
        }
    }

    private String uploadOriginal(MultipartFile file) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {

        String extension = getExtension(file.getOriginalFilename());
        String fileName = "media/" + UUID.randomUUID() + extension;

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketProduk)
                        .object(fileName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        return fileName;
    }

    private String uploadConverted(MultipartFile file) {
        try {
            byte[] webpBytes = ImageUtils.convertToWebP(file, 1200);

            String fileName = "media/" + UUID.randomUUID() + ".webp";

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketProduk)
                            .object(fileName)
                            .stream(new ByteArrayInputStream(webpBytes), webpBytes.length, -1)
                            .contentType("image/webp")
                            .build()
            );

            return fileName;
        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }

    private String getExtension(String filename) {
        return filename.substring(filename.lastIndexOf("."));
    }

    public void removeFile(String fileName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketProduk)
                            .object(fileName)
                            .build()
            );
        } catch (Exception e) {

        }
    }



}

