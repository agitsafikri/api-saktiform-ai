package com.saktiform.api.model.workspace;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


public interface WorkspaceListDto {
    Long getIdWorkspace();
    String getNamaWorkspace();
    Integer getTotalUser();
    String getNomorWaba();
    String getStatusWaba();
}
