package com.saktiform.api.model.workspace;

import com.saktiform.api.model.account.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceAccountList {
    Long id;
    String username;
    Role role;
    String name;
}
