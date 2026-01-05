package com.saktiform.api.service.chat.bot;

import org.springframework.stereotype.Service;

@Service
public class BotDelayCalculator {
    public long calculateDelaySeconds(String messageType, String text) {

        if (!"TEXT".equals(messageType)) {
            return 2; // media → cepat
        }

        if (text == null) return 3;

        int len = text.length();
        if (len < 20) return 2;
        if (len < 80) return 3;
        if (len < 200) return 4;
        return 5;
    }
}
