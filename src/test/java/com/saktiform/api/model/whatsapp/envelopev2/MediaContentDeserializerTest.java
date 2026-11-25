package com.saktiform.api.model.whatsapp.envelopev2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaContentDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private MediaContent image(String json) throws Exception {
        return mapper.readValue("{\"image\":" + json + "}", MessagePayload.class).getImage();
    }

    @Test
    void textualPayloadIsPathWithoutMime() throws Exception {
        var media = image("\"statics/media/a.jpg\"");
        assertEquals("statics/media/a.jpg", media.getPath());
        assertNull(media.getMimeType());
    }

    @Test
    void objectWithSnakeCaseKeys() throws Exception {
        var media = image("{\"media_path\":\"statics/media/a.jpg\",\"mime_type\":\"image/jpeg\",\"caption\":\"hai\",\"file_name\":\"a.jpg\"}");
        assertEquals("statics/media/a.jpg", media.getPath());
        assertEquals("image/jpeg", media.getMimeType());
        assertEquals("hai", media.getCaption());
        assertEquals("a.jpg", media.getFilename());
    }

    @Test
    void objectWithAlternativeKeys() throws Exception {
        var media = image("{\"path\":\"statics/media/a.jpg\",\"mimetype\":\"image/png\",\"filename\":\"a.png\"}");
        assertEquals("statics/media/a.jpg", media.getPath());
        assertEquals("image/png", media.getMimeType());
        assertEquals("a.png", media.getFilename());
    }

    @Test
    void missingOrNullFieldsStayNull() throws Exception {
        var media = image("{\"media_path\":\"statics/media/a.jpg\",\"mime_type\":null}");
        assertNull(media.getMimeType());
        assertNull(media.getCaption());
        assertNull(media.getFilename());
    }
}
