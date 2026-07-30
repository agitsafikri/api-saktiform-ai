package com.saktiform.api.service.blast;

import com.saktiform.api.model.chat.GoWaSendMessageRequest;
import com.saktiform.api.model.chat.SendResults;
import com.saktiform.api.model.whatsapp.WhatsappResponse;
import com.saktiform.api.service.chat.WhatsappClientHelper;
import com.saktiform.api.util.BlastPhoneMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orkestrator pengiriman satu job. TIDAK transaksional: memanggil {@link BlastSendTxService}
 * (transaksi singkat) di sekitar pemanggilan WhatsApp API, lalu sleep delay anti-ban DI LUAR transaksi.
 */
@Service
public class BlastSenderService {

    private static final Logger log = LoggerFactory.getLogger(BlastSenderService.class);

    private final BlastSendTxService txService;
    private final WhatsappClientHelper client;

    public BlastSenderService(BlastSendTxService txService, WhatsappClientHelper client) {
        this.txService = txService;
        this.client = client;
    }

    public void processJob(Long jobId) {
        SendContext ctx = txService.beginSend(jobId);
        if (ctx == null) {
            return; // di-skip (sudah terminal/interrupted/dll)
        }
        try {
            WhatsappResponse<SendResults> response;
            if (ctx.getMediaUrl() != null) {
                response = client.sendImage(ctx.getDeviceId(), ctx.getPhone(), ctx.getRenderedMessage(), ctx.getMediaUrl());
            } else {
                GoWaSendMessageRequest req = new GoWaSendMessageRequest();
                req.setPhone(ctx.getPhone());
                req.setMessage(ctx.getRenderedMessage());
                response = client.sendMessage(ctx.getDeviceId(), req);
            }

            if (isSuccess(response)) {
                txService.completeSuccess(ctx, response.getResults().getMessage_id());
            } else {
                String code = response != null ? response.getCode() : "NO_RESPONSE";
                fail(ctx, "provider code: " + code);
            }
        } catch (Exception e) {
            fail(ctx, e.getMessage());
        } finally {
            sleepDelay(ctx.getDelayMs());
        }
    }

    private void fail(SendContext ctx, String error) {
        if (looksLikeDeviceOff(error)) {
            // OQ-14: device WA kemungkinan off/disconnected → alert (WARN). Nomor di-mask (PII).
            log.warn("Blast: device WA kemungkinan off saat kirim ke {} (campaign={}): {}",
                    BlastPhoneMask.mask(ctx.getPhone()), ctx.getCampaignId(), error);
        } else {
            log.warn("Blast send gagal ke {} attempt={}: {}",
                    BlastPhoneMask.mask(ctx.getPhone()), ctx.getAttempt(), error);
        }
        txService.completeFailure(ctx, error);
    }

    private boolean looksLikeDeviceOff(String error) {
        if (error == null) return false;
        String e = error.toLowerCase();
        return e.contains("device") || e.contains("disconnect") || e.contains("not connected")
                || e.contains("logged out") || e.contains("offline") || e.contains("session");
    }

    private boolean isSuccess(WhatsappResponse<SendResults> response) {
        return response != null
                && response.getCode() != null
                && response.getCode().equalsIgnoreCase("SUCCESS")
                && response.getResults() != null
                && response.getResults().getMessage_id() != null;
    }

    private void sleepDelay(int delayMs) {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
