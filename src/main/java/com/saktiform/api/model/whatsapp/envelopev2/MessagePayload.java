package com.saktiform.api.model.whatsapp.envelopev2;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
public class MessagePayload
        extends CommonMessagePayload {

    // TEXT
    private String body;
    private String repliedToId;
    private String quotedBody;

    // FLAGS
    private Boolean forwarded;
    private Boolean viewOnce;

    // MEDIA (pakai MediaContent + deserializer)
    @JsonDeserialize(using = MediaContentDeserializer.class)
    private MediaContent image;

    @JsonDeserialize(using = MediaContentDeserializer.class)
    private MediaContent video;

    @JsonDeserialize(using = MediaContentDeserializer.class)
    private MediaContent audio;

    @JsonDeserialize(using = MediaContentDeserializer.class)
    private MediaContent document;

    @JsonDeserialize(using = MediaContentDeserializer.class)
    private MediaContent sticker;

    @JsonDeserialize(using = MediaContentDeserializer.class)
    private MediaContent videoNote;
}
