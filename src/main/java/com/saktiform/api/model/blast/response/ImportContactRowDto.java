package com.saktiform.api.model.blast.response;

import com.saktiform.api.entity.BlastImportContact;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Baris staging untuk preview/review hasil analisis per kategori.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportContactRowDto {
    private Integer rowNumber;
    private String rawName;
    private String rawPhone;
    private String normalizedPhone;
    private String category;
    private String invalidReason;

    public static ImportContactRowDto from(BlastImportContact c) {
        return new ImportContactRowDto(
                c.getRowNumber(), c.getRawName(), c.getRawPhone(),
                c.getNormalizedPhone(), c.getCategory(), c.getInvalidReason());
    }
}
