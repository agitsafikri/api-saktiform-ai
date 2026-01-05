package com.saktiform.api.model.whatsapp;

public record MediaResult(String mediaType,
                          String fileName,
                          String localPath,
                          String publicUrl) {
}
