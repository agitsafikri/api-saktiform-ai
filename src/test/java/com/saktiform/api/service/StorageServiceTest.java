package com.saktiform.api.service;

import io.minio.CopyObjectArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageServiceTest {

    private static final String BASE = "https://cdn.example.com";

    private MinioClient minioClient;
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        storageService = new StorageService(minioClient);
        ReflectionTestUtils.setField(storageService, "bucket", "saktiform-media");
        ReflectionTestUtils.setField(storageService, "bucketProduk", "produk-media");
        ReflectionTestUtils.setField(storageService, "prefix", "media");
        ReflectionTestUtils.setField(storageService, "publicBaseUrl", BASE);
    }

    @Test
    void chatBucketUrlIsExtractedWithoutCopy() throws Exception {
        var path = storageService.toChatBucketPath(BASE + "/saktiform-media/media/2026/10/a.jpg");

        assertEquals("media/2026/10/a.jpg", path);
        verify(minioClient, never()).copyObject(any());
    }

    @Test
    void produkBucketUrlIsCopiedToChatBucket() throws Exception {
        var path = storageService.toChatBucketPath(BASE + "/produk-media/media/abc.webp");

        assertTrue(path.matches("media/\\d{4}/\\d{1,2}/[0-9a-f-]{36}\\.webp"), path);

        ArgumentCaptor<CopyObjectArgs> args = ArgumentCaptor.forClass(CopyObjectArgs.class);
        verify(minioClient).copyObject(args.capture());
        assertEquals("saktiform-media", args.getValue().bucket());
        assertEquals(path, args.getValue().object());
        assertEquals("produk-media", args.getValue().source().bucket());
        assertEquals("media/abc.webp", args.getValue().source().object());
    }

    @Test
    void invalidUrlIsRejected() throws Exception {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> storageService.toChatBucketPath("https://evil.example.com/x.jpg"));
        assertEquals("URL tidak valid untuk bucket ini", ex.getMessage());
        verify(minioClient, never()).copyObject(any());
    }

    @Test
    void copyFailureIsWrapped() throws Exception {
        when(minioClient.copyObject(any())).thenThrow(new RuntimeException("minio down"));

        var ex = assertThrows(RuntimeException.class,
                () -> storageService.toChatBucketPath(BASE + "/produk-media/media/abc.webp"));
        assertEquals("Gagal menyalin media template ke penyimpanan chat", ex.getMessage());
    }
}
