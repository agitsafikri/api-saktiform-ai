package com.saktiform.api.model.blast.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Response ringkas aksi campaign (create/start/pause/resume/cancel).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignResponse {
    private UUID campaignId;
    private String status;
    private Integer totalRecipient;
}
