package com.saktiform.api.util;

import com.saktiform.api.model.whatsapp.MediaResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class MediaHelper {
    @Value("${media.base.directory}")
    private String MEDIA_BASE_DIRECTORY;

    private static final Map<String, Set<String>> MEDIA_WHITELIST = Map.of(
            "image", Set.of(".jpg", ".jpeg", ".png", ".webp"),
            "audio", Set.of(".ogg", ".mp3", ".wav", ".m4a"),
            "video", Set.of(".mp4", ".webm", ".3gp"),
            "document", Set.of(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt")
    );

    public MediaResult saveMediaFromUrl(String mediaUrl, String mediaType, String messageId) {

        try {
            String mime = mediaType.toLowerCase();
            if (mime.contains("/")) {
                String category = mime.split("/")[0];    // image
                String subType  = mime.split("/")[1];
                mediaType = category;
            }

            if (!MEDIA_WHITELIST.containsKey(mediaType)) {
                throw new IllegalArgumentException("Unsupported media type: " + mediaType);
            }

            String ext = resolveExtension(mediaUrl, mediaType);
            String safeName = "wa_" + messageId + ext;

            Path targetDir = Paths.get(MEDIA_BASE_DIRECTORY+"/whatsapp", mediaType).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);

            Path targetFile = targetDir.resolve(safeName);

            try (InputStream in = new URL(mediaUrl).openStream()) {
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return new MediaResult(
                    mediaType,
                    safeName,
                    targetFile.toString(),
                    "/media/" + mediaType + "/" + safeName
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to store media", e);
        }
    }

    private String resolveExtension(String url, String mediaType) {
        String ext = extractExtFromUrl(url);

        Set<String> allowed = MEDIA_WHITELIST.get(mediaType);
        if (allowed.contains(ext)) {
            return ext;
        }

        return fallbackByMediaType(mediaType);
    }

    private String extractExtFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null || !path.contains(".")) return "";
            return path.substring(path.lastIndexOf(".")).toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    private String fallbackByMediaType(String mediaType) {
        return switch (mediaType) {
            case "image" -> ".jpg";
            case "audio" -> ".ogg";
            case "video" -> ".mp4";
            case "document" -> ".pdf";
            default -> throw new IllegalArgumentException("Unknown media type");
        };
    }

    public String saveFile(MultipartFile file){
        try {
            // Nama file unik
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            // Simpan ke folder "uploads" di luar JAR
            Path uploadPath = Paths.get(MEDIA_BASE_DIRECTORY+"/uploads").toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve(fileName);
            Files.write(filePath, file.getBytes());

            // URL publik (otomatis serve oleh Spring)
            String fileUrl = "/uploads/" + fileName;
            return fileUrl;

        } catch (IOException e) {
            throw new RuntimeException("Failed", e);
        }
    }
}
