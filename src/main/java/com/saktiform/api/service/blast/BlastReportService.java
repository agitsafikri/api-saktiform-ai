package com.saktiform.api.service.blast;

import com.saktiform.api.entity.BlastCampaign;
import com.saktiform.api.entity.BlastMessage;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.repository.BlastCampaignRepository;
import com.saktiform.api.repository.BlastMessageRepository;
import com.saktiform.api.repository.WorkspaceRepository;
import com.saktiform.api.service.StorageService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Generate Report campaign ke Excel (.xlsx) streaming (SXSSF), format kolom tetap sesuai Appendix F PRD
 * (sheet "Messages", 22 kolom: pesan keluar + balasan pertama). Nilai sel di-sanitasi anti formula injection.
 */
@Service
public class BlastReportService {

    private static final String[] HEADERS = {
            "phone_number", "name", "id", "campaign_id", "conversation_id", "created_at",
            "media_type", "media_url", "message", "sent_by", "sent_by_name", "sent_by_type",
            "status", "error", "is_replied", "first_reply_id", "first_reply_message",
            "first_reply_sent_by", "first_reply_sent_by_name", "first_reply_media_url",
            "first_reply_media_type", "first_reply_created_at"
    };
    private static final int PAGE_SIZE = 1000;

    private final BlastCampaignRepository campaignRepository;
    private final BlastMessageRepository messageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final StorageService storageService;

    public BlastReportService(BlastCampaignRepository campaignRepository,
                              BlastMessageRepository messageRepository,
                              WorkspaceRepository workspaceRepository,
                              StorageService storageService) {
        this.campaignRepository = campaignRepository;
        this.messageRepository = messageRepository;
        this.workspaceRepository = workspaceRepository;
        this.storageService = storageService;
    }

    /** Nama file: {campaign}_messages_{yyyy-MM-dd}.xlsx (FR-14.5). */
    public String buildFileName(String campaignName) {
        String safe = (campaignName == null || campaignName.isBlank() ? "campaign" : campaignName)
                .trim().replaceAll("[^a-zA-Z0-9-_]+", "_");
        String date = LocalDate.now(ZoneId.of("Asia/Jakarta")).toString();
        return safe + "_messages_" + date + ".xlsx";
    }

    public void generateReport(UUID campaignId, Long workspaceId, OutputStream out) {
        BlastCampaign c = campaignRepository.findByIdAndIdWorkspace(campaignId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign tidak ditemukan"));

        String sentByName = workspaceRepository.findById(workspaceId)
                .map(Workspace::getNamaWorkspace).orElse("");
        String sentBy = c.getDeviceId() != null ? c.getDeviceId() : "";
        boolean hasMedia = c.getMediaLink() != null && !c.getMediaLink().isBlank();
        String mediaType = hasMedia ? "image" : "text";
        String mediaUrl = hasMedia ? publicUrl(c.getMediaLink()) : "";
        String campaignIdStr = c.getId().toString();

        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        try {
            Sheet sheet = wb.createSheet("Messages");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }

            int rowIdx = 1;
            int page = 0;
            Page<BlastMessage> pg;
            do {
                pg = messageRepository.findByCampaignId(campaignId,
                        PageRequest.of(page, PAGE_SIZE, Sort.by("id").ascending()));
                for (BlastMessage m : pg.getContent()) {
                    writeRow(sheet.createRow(rowIdx++), m, campaignIdStr, mediaType, mediaUrl, sentBy, sentByName);
                }
                page++;
            } while (pg.hasNext());

            wb.write(out);
        } catch (Exception e) {
            throw new RuntimeException("Gagal generate report: " + e.getMessage(), e);
        } finally {
            wb.dispose(); // hapus temp files SXSSF
        }
    }

    private void writeRow(Row row, BlastMessage m, String campaignIdStr, String mediaType,
                          String mediaUrl, String sentBy, String sentByName) {
        str(row, 0, m.getPhone());
        str(row, 1, m.getName());
        str(row, 2, String.valueOf(m.getId()));
        str(row, 3, campaignIdStr);
        str(row, 4, m.getConversationId() != null ? m.getConversationId().toString() : "");
        str(row, 5, iso(m.getSentAt()));
        str(row, 6, mediaType);
        str(row, 7, mediaUrl);
        str(row, 8, m.getRenderedMessage() != null ? m.getRenderedMessage() : "");
        str(row, 9, sentBy);
        str(row, 10, sentByName);
        str(row, 11, "campaigns");
        str(row, 12, m.getStatus() != null ? m.getStatus().toLowerCase() : "");
        str(row, 13, m.getLastError());
        row.createCell(14).setCellValue(m.getRepliedAt() != null); // is_replied (boolean)
        str(row, 15, m.getFirstReplyChatId() != null ? m.getFirstReplyChatId().toString() : "");
        str(row, 16, m.getFirstReplyMessage());
        str(row, 17, m.getContactId() != null ? String.valueOf(m.getContactId()) : "");
        str(row, 18, m.getName());
        str(row, 19, m.getFirstReplyMediaLink() != null ? publicUrl(m.getFirstReplyMediaLink()) : "");
        str(row, 20, m.getFirstReplyMediaType());
        str(row, 21, iso(m.getRepliedAt()));
    }

    private void str(Row row, int idx, String value) {
        row.createCell(idx).setCellValue(sanitize(value));
    }

    /** Anti Excel formula injection (FR-14.6): prefix ' bila sel diawali = + - @. */
    private String sanitize(String v) {
        if (v == null || v.isEmpty()) return "";
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + v;
        }
        return v;
    }

    private String iso(Instant t) {
        return t == null ? "" : t.toString();
    }

    private String publicUrl(String mediaLink) {
        if (mediaLink == null || mediaLink.isBlank()) return "";
        String lower = mediaLink.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) return mediaLink;
        return storageService.getPublicUrl(mediaLink);
    }
}
