package com.saktiform.api.entity;

import com.saktiform.api.model.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "account")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nama", length = Integer.MAX_VALUE)
    private String nama;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = Integer.MAX_VALUE)
    private Role role;

    @Column(name = "username", length = Integer.MAX_VALUE)
    private String username;

    @Column(name = "password", length = Integer.MAX_VALUE)
    private String password;

//    @Column(name = "id_workspace")
//    private Long idWorkspace;

    @ManyToMany
    @JoinTable(
            name = "account_workspace",
            joinColumns = @JoinColumn(name = "id_account"),
            inverseJoinColumns = @JoinColumn(name = "id_workspace")
    )
    private Set<Workspace> workspaces;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name="is_deleted")
    Boolean isDeleted;

}