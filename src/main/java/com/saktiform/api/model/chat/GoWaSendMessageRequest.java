package com.saktiform.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoWaSendMessageRequest {
    public String phone;
    public String message;
    public String reply_message_id;
    public boolean is_forwarded;
    public int duration;
}
