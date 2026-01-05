package com.saktiform.api.model;

import com.saktiform.api.model.workspace.WorkspaceDropdownDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String token;
    private String username;
    private String nama;
    private String role;
    private List<WorkspaceDropdownDto> workspaces;
}

