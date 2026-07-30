package com.saktiform.api.service.blast;

import com.saktiform.api.entity.BlastImport;
import com.saktiform.api.model.blast.enums.ContactCategory;
import com.saktiform.api.model.blast.enums.ImportStatus;
import com.saktiform.api.model.blast.response.ImportResponseDto;
import com.saktiform.api.model.blast.response.ImportSummaryDto;
import com.saktiform.api.repository.BlastImportContactRepository;
import com.saktiform.api.repository.BlastImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Analisis kontak: klasifikasi staging (DUPLICATE/EXISTING/NEW; INVALID sudah ditandai saat upload),
 * lalu hitung & simpan summary. Idempotent — dapat di-run ulang dari status UPLOADED/ANALYZED.
 * Dipicu {@code BlastImportAnalyzeListener} (cross-bean → transaksi sendiri).
 */
@Service
public class BlastAnalysisService {

    private final BlastImportRepository importRepository;
    private final BlastImportContactRepository contactRepository;

    public BlastAnalysisService(BlastImportRepository importRepository,
                                BlastImportContactRepository contactRepository) {
        this.importRepository = importRepository;
        this.contactRepository = contactRepository;
    }

    @Transactional
    public ImportResponseDto analyze(Long importId, Long workspaceId) {
        BlastImport imp = importRepository.findByIdAndIdWorkspace(importId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Import tidak ditemukan"));

        String status = imp.getStatus();
        if (ImportStatus.ANALYZING.name().equals(status)) {
            return toResponse(imp); // sedang berjalan
        }
        if (!ImportStatus.UPLOADED.name().equals(status) && !ImportStatus.ANALYZED.name().equals(status)) {
            throw new IllegalStateException("Import tidak dapat dianalisis pada status " + status);
        }

        imp.setStatus(ImportStatus.ANALYZING.name());
        imp.setUpdatedAt(Instant.now());
        importRepository.save(imp);

        // Urutan penting: duplikat dulu (agar tidak terhitung existing), lalu existing via JOIN contact.
        contactRepository.markDuplicates(importId);
        contactRepository.markExistingByWorkspace(importId, workspaceId);

        int invalid = (int) contactRepository.countByImportIdAndCategoryIn(importId, List.of(ContactCategory.INVALID.name()));
        int duplicate = (int) contactRepository.countByImportIdAndCategoryIn(importId, List.of(ContactCategory.DUPLICATE.name()));
        int existing = (int) contactRepository.countByImportIdAndCategoryIn(importId, List.of(ContactCategory.EXISTING.name()));
        int neu = (int) contactRepository.countByImportIdAndCategoryIn(importId, List.of(ContactCategory.NEW.name()));

        imp.setTotalInvalid(invalid);
        imp.setTotalDuplicate(duplicate);
        imp.setTotalExisting(existing);
        imp.setTotalNew(neu);
        imp.setTotalValid(existing + neu);
        imp.setStatus(ImportStatus.ANALYZED.name());
        imp.setUpdatedAt(Instant.now());
        importRepository.save(imp);

        return toResponse(imp);
    }

    private ImportResponseDto toResponse(BlastImport imp) {
        ImportSummaryDto summary = new ImportSummaryDto(
                imp.getTotalUpload(), imp.getTotalValid(), imp.getTotalInvalid(),
                imp.getTotalDuplicate(), imp.getTotalExisting(), imp.getTotalNew());
        return new ImportResponseDto(imp.getId(), imp.getFileName(), imp.getStatus(), imp.getTotalUpload(), summary);
    }
}
