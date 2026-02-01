package com.saktiform.api.model.whatsapp.envelopev2;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
public class ReactionPayload extends CommonMessagePayload {

    private String reaction;
    private String reactedMessageId;
}

