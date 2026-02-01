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
        if (node.isObject()) {
            if (node.has("url")) {
                media.setUrl(node.get("url").asText());
            }

            if (node.has("mime_type")) {
                media.setMimeType(node.get("mime_type").asText());
            }

            if (node.has("media_path")) {
                media.setPath(node.get("media_path").asText());
            }

            if (node.has("caption")) {
                media.setCaption(node.get("caption").asText());
            }
            if (node.has("file_name")) {
                media.setFilename(node.get("filename").asText());
            }
        }

        return media;
    }
}
