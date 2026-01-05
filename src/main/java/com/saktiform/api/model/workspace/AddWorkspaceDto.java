package com.saktiform.api.model.workspace;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for {@link com.saktiform.api.entity.Workspace}
 */
@Value
public class AddWorkspaceDto implements Serializable {
    Long id;
    @NotNull
    String namaWorkspace;
    @NotNull
    UUID wabaId;
    List<Long> idUsers = new ArrayList<>();
    @NotNull
    GudangDto gudang;
}