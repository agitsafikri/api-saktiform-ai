package com.saktiform.api.model.whatsapp.envelopev2;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

public class MediaContentDeserializer
        extends JsonDeserializer<MediaContent> {

    @Override
    public MediaContent deserialize(
            JsonParser p,
            DeserializationContext ctxt
    ) throws IOException {

        JsonNode node = p.getCodec().readTree(p);
        MediaContent media = new MediaContent();

        // case: "image": "path/to/file"
        if (node.isTextual()) {
            media.setPath(node.asText());
            return media;
        }

        // case: "image": { "url": "...", "caption": "..." }
        // Nama field berbeda antar versi gowa → terima beberapa alias.
        if (node.isObject()) {
            media.setUrl(text(node, "url"));
            media.setMimeType(text(node, "mime_type", "mimetype", "mimeType"));
            media.setPath(text(node, "media_path", "path"));
            media.setCaption(text(node, "caption"));
            media.setFilename(text(node, "file_name", "filename", "fileName"));
        }

        return media;
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }
}
