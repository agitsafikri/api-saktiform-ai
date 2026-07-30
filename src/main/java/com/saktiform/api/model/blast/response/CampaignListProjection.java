package com.saktiform.api.model.blast.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Interface projection untuk list campaign (native query). Alias kolom SQL → getter camelCase.
 */
public interface CampaignListProjection {
    UUID getId();
    String getName();
    String getStatus();
    Integer getTotalRecipient();
    Integer getCountSent();
    Integer getCountFailed();
    Integer getCountReplied();

    @JsonIgnore
    Instant getCreatedAtRaw();

    default String getCreatedAt() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        if (getCreatedAtRaw() == null) return "";
        return getCreatedAtRaw().atZone(ZoneId.of("Asia/Jakarta")).format(formatter);
    }
}
