package com.saktiform.api.model.blast.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response upload / analyze / detail import.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportResponseDto {
    private Long importId;
    private String fileName;
    private String status;
    private Integer totalUpload;
    private ImportSummaryDto summary;   // null sebelum ANALYZED
}
