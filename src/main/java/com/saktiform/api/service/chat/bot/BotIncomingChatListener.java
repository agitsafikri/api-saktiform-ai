package com.saktiform.api.service.chat.bot;

import com.saktiform.api.model.chat.bot.IncomingChatEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BotIncomingChatListener {
    private final BotOrchestratorService botOrchestratorService;
    public BotIncomingChatListener(BotOrchestratorService botOrchestratorService) {
        this.botOrchestratorService = botOrchestratorService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleIncomingChat(IncomingChatEvent event) {

        botOrchestratorService.onIncomingChat(
                event
        );
    }
}
