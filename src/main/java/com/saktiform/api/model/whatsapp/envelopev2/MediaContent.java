package com.saktiform.api.model.whatsapp.envelopev2;

import lombok.Data;

@Data
public class MediaContent {

    /**
     * Jika auto-download ON
     */
    private String path;

    /**
     * Jika auto-download OFF
     */
    private String url;

    private String caption;
    private String filename;
    private String mimeType;
}
