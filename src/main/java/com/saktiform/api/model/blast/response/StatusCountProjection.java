package com.saktiform.api.model.blast.response;

/**
 * Projection untuk rekonsiliasi counter: jumlah blast_message per status.
 */
public interface StatusCountProjection {
    String getStatus();
    Long getCnt();
}
