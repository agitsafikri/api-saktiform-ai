package com.saktiform.api.service.blast;

import com.saktiform.api.entity.BlastImport;
import com.saktiform.api.entity.BlastImportContact;
import com.saktiform.api.model.blast.enums.ContactCategory;
import com.saktiform.api.model.blast.enums.ImportStatus;
import com.saktiform.api.model.blast.event.BlastImportUploadedEvent;
import com.saktiform.api.model.blast.response.ImportContactRowDto;
import com.saktiform.api.model.blast.response.ImportResponseDto;
import com.saktiform.api.model.blast.response.ImportSummaryDto;
import com.saktiform.api.repository.BlastImportContactRepository;
import com.saktiform.api.repository.BlastImportRepository;
import com.saktiform.api.util.PhoneNumberUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class BlastImportService {

    private final BlastImportRepository importRepository;
    private final BlastImportContactRepository contactRepository;
    private final ExcelParser excelParser;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${blast.upload.max-rows:20000}")
    private int maxRows;

    @Value("${blast.upload.max-file-size-bytes:2097152}")
    private long maxFileBytes;

    public BlastImportService(BlastImportRepository importRepository,
                              BlastImportContactRepository contactRepository,
                              ExcelParser excelParser,
                              ApplicationEventPublisher eventPublisher) {
        this.importRepository = importRepository;
        this.contactRepository = contactRepository;
        this.excelParser = excelParser;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ImportResponseDto handleUpload(MultipartFile file, Long workspaceId, Long accountId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File kosong");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = original.toLowerCase();
        if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
            throw new IllegalArgumentException("Tipe file tidak didukung. Hanya .xlsx/.xls");
        }
        if (file.getSize() > maxFileBytes) {
            throw new IllegalArgumentException("Ukuran file melebihi batas " + (maxFileBytes / 1024 / 1024) + " MB");
        }

        List<ExcelParser.ParsedRow> rows = excelParser.parse(file); // throw → 400 (parseability/header)
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Tidak ada baris data pada file");
        }
        if (rows.size() > maxRows) {
            throw new IllegalArgumentException("Jumlah baris (" + rows.size() + ") melebihi batas " + maxRows);
        }

        Instant now = Instant.now();
        BlastImport imp = new BlastImport();
        imp.setIdWorkspace(workspaceId);
        imp.setCreatedBy(accountId);
        imp.setFileName(original);
        imp.setStatus(ImportStatus.UPLOADED.name());
        imp.setTotalUpload(rows.size());
        imp.setCreatedAt(now);
        imp = importRepository.save(imp);

        List<BlastImportContact> staging = new ArrayList<>(rows.size());
        for (ExcelParser.ParsedRow pr : rows) {
            BlastImportContact bic = new BlastImportContact();
            bic.setImportId(imp.getId());
            bic.setIdWorkspace(workspaceId);
            bic.setRowNumber(pr.rowNumber());
            bic.setRawName(pr.rawName());
            bic.setRawPhone(pr.rawPhone());
            String normalized = PhoneNumberUtil.normalizeToIndonesianFormat(pr.rawPhone());
            bic.setNormalizedPhone(normalized);
            if (isValidNumber(normalized)) {
                bic.setCategory(ContactCategory.NEW.name());   // tentatif; analisis memperbaiki ke EXISTING/DUPLICATE
            } else {
                bic.setCategory(ContactCategory.INVALID.name());
                bic.setInvalidReason(invalidReason(pr.rawPhone(), normalized));
            }
            bic.setCreatedAt(now);
            staging.add(bic);
        }
        contactRepository.saveAll(staging);

        // Analisis otomatis & async setelah commit (OQ-1)
        eventPublisher.publishEvent(new BlastImportUploadedEvent(imp.getId(), workspaceId));

        return new ImportResponseDto(imp.getId(), imp.getFileName(), imp.getStatus(), imp.getTotalUpload(), null);
    }

    @Transactional(readOnly = true)
    public ImportResponseDto getImport(Long importId, Long workspaceId) {
        BlastImport imp = importRepository.findByIdAndIdWorkspace(importId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Import tidak ditemukan"));
        ImportSummaryDto summary = new ImportSummaryDto(
                imp.getTotalUpload(), imp.getTotalValid(), imp.getTotalInvalid(),
                imp.getTotalDuplicate(), imp.getTotalExisting(), imp.getTotalNew());
        return new ImportResponseDto(imp.getId(), imp.getFileName(), imp.getStatus(), imp.getTotalUpload(), summary);
    }

    @Transactional(readOnly = true)
    public Page<ImportContactRowDto> getContacts(Long importId, Long workspaceId, String category, int page, int limit) {
        importRepository.findByIdAndIdWorkspace(importId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Import tidak ditemukan"));
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), limit);
        return contactRepository.findByImportIdAndCategory(importId, category, pageable)
                .map(ImportContactRowDto::from);
    }

    /** Valid bila ternormalisasi berprefix 62 dan total panjang 10–15 digit (BR-3). */
    private boolean isValidNumber(String normalized) {
        return normalized != null && normalized.matches("62\\d{8,13}");
    }

    private String invalidReason(String raw, String normalized) {
        if (raw == null || raw.isBlank()) return "nomor kosong";
        if (normalized == null || !normalized.startsWith("62")) return "format nomor tidak valid";
        return "panjang nomor di luar 10-15 digit";
    }
}
