package com.saktiform.api.model.label.response;

import java.util.UUID;

/** Interface projection batch fetch label per conversation (anti N+1). */
public interface ConversationLabelProjection {
    UUID getConversationId();

    Long getId();

    String getName();

    String getColorHex();
}
