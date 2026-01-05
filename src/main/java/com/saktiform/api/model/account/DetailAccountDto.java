package com.saktiform.api.model.account;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetailAccountDto {
    Long id;
    String nama;
    String username;
    String role;
    ArrayList<WorkspaceAccount> workspaces = new ArrayList<>();
}
