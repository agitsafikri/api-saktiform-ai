package com.saktiform.api.model.workspace;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkspaceDto {
    @NotNull
    Long id;
    @NotNull
    String namaWorkspace;
    @NotNull
    UUID wabaId;
}
