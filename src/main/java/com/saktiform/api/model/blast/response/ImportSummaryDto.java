package com.saktiform.api.model.blast.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ringkasan hasil analisis kontak. Invariant: totalUpload = invalid + duplicate + existing + new.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportSummaryDto {
    private Integer totalUpload;
    private Integer totalValid;
    private Integer totalInvalid;
    private Integer totalDuplicate;
    private Integer existingContact;
    private Integer newContact;
}
