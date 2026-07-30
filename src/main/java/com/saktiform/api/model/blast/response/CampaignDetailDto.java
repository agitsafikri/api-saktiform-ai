package com.saktiform.api.model.blast.response;

import com.saktiform.api.entity.BlastCampaign;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDetailDto {
    private UUID id;
    private String name;
    private String status;
    private String messageSource;
    private String targetType;
    private String messageContent;
    private String mediaLink;
    private Integer totalRecipient;
    private String createdAt;
    private String startedAt;
    private String finishedAt;
    private CampaignProgressDto progress;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static CampaignDetailDto from(BlastCampaign c) {
        return new CampaignDetailDto(
                c.getId(), c.getName(), c.getStatus(), c.getMessageSource(), c.getTargetType(),
                c.getMessageContent(), c.getMediaLink(), c.getTotalRecipient(),
                fmt(c.getCreatedAt()), fmt(c.getStartedAt()), fmt(c.getFinishedAt()),
                CampaignProgressDto.from(c));
    }

    private static String fmt(Instant t) {
        return t == null ? null : t.atZone(ZoneId.of("Asia/Jakarta")).format(FMT);
    }
}
