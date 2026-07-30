package com.saktiform.api.model.blast.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Dipublish setelah upload Excel selesai; memicu analisis kontak async (OQ-1).
 */
@Getter
@Setter
@AllArgsConstructor
public class BlastImportUploadedEvent {
    private Long importId;
    private Long workspaceId;
}
