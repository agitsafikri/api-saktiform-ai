package com.saktiform.api.model.blast.response;

import com.saktiform.api.entity.BlastCampaign;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignProgressDto {
    private String status;
    private int total;
    private int waiting;
    private int sending;
    private int success;   // kumulatif terkirim (termasuk delivered/read/replied)
    private int failed;
    private int replied;
    private int skipped;
    private double percentage;

    public static CampaignProgressDto from(BlastCampaign c) {
        int total = nz(c.getTotalRecipient());
        int success = nz(c.getCountSent());
        int failed = nz(c.getCountFailed());
        int skipped = nz(c.getCountSkipped());
        double percentage = total > 0
                ? BigDecimal.valueOf((success + failed + skipped) * 100.0 / total)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        return new CampaignProgressDto(
                c.getStatus(), total, nz(c.getCountWaiting()), nz(c.getCountSending()),
                success, failed, nz(c.getCountReplied()), skipped, percentage);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
