package com.saktiform.api.model.blast.response;

import com.saktiform.api.entity.BlastMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageListDto {
    private Long id;
    private String phone;
    private String name;
    private String status;
    private Integer retryCount;
    private String lastError;
    private String sentAt;
    private String repliedAt;
    private String firstReplyMessage;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static MessageListDto from(BlastMessage m) {
        return new MessageListDto(
                m.getId(), m.getPhone(), m.getName(), m.getStatus(), m.getRetryCount(),
                m.getLastError(), fmt(m.getSentAt()), fmt(m.getRepliedAt()), m.getFirstReplyMessage());
    }

    private static String fmt(Instant t) {
        return t == null ? null : t.atZone(ZoneId.of("Asia/Jakarta")).format(FMT);
    }
}
