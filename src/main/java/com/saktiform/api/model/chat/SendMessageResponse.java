package com.saktiform.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SendMessageResponse {
    String code;
    String message;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    private class Results {
        String message_id;
        String status;
    }
    Results results;
}
