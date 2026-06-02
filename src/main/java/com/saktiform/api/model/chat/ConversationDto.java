package com.saktiform.api.model.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;


public interface ConversationDto {
    UUID getId();
    String getContactName();
    String getLastMessage();
    String getLastMessageType();
    default String getLastMessageTime(){
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        if (getLastMessageTimeRaw() == null) return "";
        return getLastMessageTimeRaw().atZone(ZoneId.of("Asia/Jakarta")).format(formatter);
    };
    @JsonIgnore
    Instant getLastMessageTimeRaw();
    String getStatus();
    String getChatStatus();
    Integer getUnreadMessageCount();
}
