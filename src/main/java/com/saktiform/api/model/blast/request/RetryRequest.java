package com.saktiform.api.model.blast.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Retry pesan FAILED. messageIds null/empty = retry SEMUA yang FAILED di campaign.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetryRequest {
    private List<Long> messageIds;
}
