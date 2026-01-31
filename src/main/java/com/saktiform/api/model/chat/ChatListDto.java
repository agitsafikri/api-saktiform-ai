package com.saktiform.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ChatListDto {
    UUID id;
    String type;
    String pengirim;
    String text;
    String mediaLink;
    String tanggal;

    private  static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");


    public ChatListDto(UUID id, String type, String pengirim, String text, String mediaLink, Instant tanggal) {
        this.id = id;
        this.type = type;
        this.pengirim = pengirim;
        this.text = text;
        this.mediaLink = mediaLink;
        this.tanggal = tanggal.atZone(ZoneId.of("Asia/Jakarta"))
                .format(formatter);
    }
}
