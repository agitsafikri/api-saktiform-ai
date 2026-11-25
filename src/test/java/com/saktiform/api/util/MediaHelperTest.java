package com.saktiform.api.util;

import com.saktiform.api.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaHelperTest {

    @TempDir
    Path tmp;

    private StorageService storageService;
    private MediaHelper mediaHelper;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        mediaHelper = new MediaHelper(storageService);
        when(storageService.getPublicUrl(anyString())).thenAnswer(inv -> "https://cdn/chat/" + inv.getArgument(0));
    }

    private String fileUrl(String name) throws Exception {
        Path file = tmp.resolve(name);
        Files.writeString(file, "data");
        return file.toUri().toString();
    }

    @Test
    void nullMimeUsesCategoryAndGuessesContentType() throws Exception {
        var result = mediaHelper.saveMediaFromUrl(fileUrl("a.jpg"), "image", null, "m1");

        assertEquals("media/whatsapp/image/wa_m1.jpg", result.localPath());
        verify(storageService).upload(any(), eq("media/whatsapp/image/wa_m1.jpg"), eq("image/jpeg"));
    }

    @Test
    void documentWithApplicationMimeIsAccepted() throws Exception {
        var result = mediaHelper.saveMediaFromUrl(fileUrl("doc.pdf"), "document", "application/pdf", "m2");

        assertEquals("media/whatsapp/document/wa_m2.pdf", result.localPath());
        verify(storageService).upload(any(), eq("media/whatsapp/document/wa_m2.pdf"), eq("application/pdf"));
    }

    @Test
    void unknownExtensionFallsBackByCategory() throws Exception {
        var result = mediaHelper.saveMediaFromUrl(fileUrl("voice"), "audio", "audio/ogg; codecs=opus", "m3");

        assertEquals("media/whatsapp/audio/wa_m3.ogg", result.localPath());
        verify(storageService).upload(any(), eq("media/whatsapp/audio/wa_m3.ogg"), eq("audio/ogg; codecs=opus"));
    }

    @Test
    void unsupportedCategoryFails() {
        var ex = assertThrows(RuntimeException.class,
                () -> mediaHelper.saveMediaFromUrl("file:/x", "sticker", null, "m4"));
        assertEquals("Failed to store media", ex.getMessage());
        verifyNoInteractions(storageService);
    }
}
