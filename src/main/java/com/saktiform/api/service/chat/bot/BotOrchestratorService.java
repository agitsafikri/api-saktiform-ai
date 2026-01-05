package com.saktiform.api.service.chat.bot;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.model.chat.bot.IncomingChatEvent;
import com.saktiform.api.service.chat.ChatMessageService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BotOrchestratorService {
    private final BotDelayCalculator botDelayCalculator;
    private final BotDelayManager botDelayManager;
    private final BotDecisionService botDecisionService;
    private final ChatMessageService chatMessageService;
    private final BotService botService;


    public BotOrchestratorService(BotDelayCalculator botDelayCalculator, BotDelayManager botDelayManager, BotDecisionService botDecisionService, ChatMessageService chatMessageService, BotService botService) {
        this.botDelayCalculator = botDelayCalculator;
        this.botDelayManager = botDelayManager;
        this.botDecisionService = botDecisionService;
        this.chatMessageService = chatMessageService;
        this.botService = botService;

    }

    public void onIncomingChat(IncomingChatEvent event){
        var chat = chatMessageService.findById(event.getChatId());
        if (!botDecisionService.shouldBotReply(chat)) {
            return;
        }

        long delay = botDelayCalculator
                .calculateDelaySeconds(chat.getType(), chat.getPesan());

        Instant receivedAt = chat.getSentAt();

        botDelayManager.debounce(
                chat.getIdConversation(),
                () -> {
                    if (chatMessageService.hasNewMessageAfter(chat.getIdConversation(), receivedAt)) {
                        return;
                    }

                    botService.handleBotReply(chat.getIdConversation());
                },
                delay
        );
    }
}
