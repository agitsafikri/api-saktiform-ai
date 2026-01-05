package com.saktiform.api.model.workspace;

import com.saktiform.api.model.account.AccountDropdownDto;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetailWorkspace {
    Long id;
    String namaWorkspace;
    UUID wabaId;
    List<AccountDropdownDto> users = new ArrayList<>();
    GudangDto gudang;
}
