package com.saktiform.api.model.blast.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Dipublish saat campaign di-start (DRAFT→QUEUED). Memicu generate recipient + queue async (OQ-12).
 */
@Getter
@Setter
@AllArgsConstructor
public class BlastCampaignStartEvent {
    private UUID campaignId;
}
