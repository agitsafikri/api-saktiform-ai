package com.saktiform.api.model.account;

import com.saktiform.api.validators.NoSpace;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RegisterRequest {
    private Long id;
    @NotBlank(message = "Nama tidak boleh kosong")
    private String nama;
    @NoSpace(message = "Nama tidak boleh ada spasi")
    @NotBlank(message = "Username tidak boleh kosong")
    private String username;
    @NotBlank(message = "Password tidak boleh kosong")
    private String password;
    private Role role;
    private List<Long> idWorkspaces = new ArrayList<>();
}
