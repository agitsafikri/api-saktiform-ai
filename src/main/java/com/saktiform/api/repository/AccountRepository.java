package com.saktiform.api.repository;

import com.saktiform.api.entity.Account;
import com.saktiform.api.model.account.AccountDropdownDto;
import com.saktiform.api.model.account.AccountListDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    @Query(value = "SELECT a from Account a where a.username = :username AND  COALESCE(a.isDeleted, false) = false")
    Optional<Account> findByUsername(@Param("username") String username);

    @Query(value = """
        SELECT a.id,
                       a.nama,
                       a.role,
                       a.username,
                        COALESCE(STRING_AGG(w.nama_workspace, ', '), '') AS workspaces
                FROM account a
                LEFT JOIN account_workspace aw ON aw.id_account = a.id
                LEFT JOIN workspace w ON w.id = aw.id_workspace
                WHERE COALESCE(a.is_deleted, false) = false
                GROUP BY a.id, a.nama, a.role, a.username
                
                
    """,
            countQuery = """
            SELECT count(*)
                FROM account a
                LEFT JOIN account_workspace aw ON aw.id_account = a.id
                LEFT JOIN workspace w ON w.id = aw.id_workspace
                WHERE COALESCE(a.is_deleted, false) = false
                GROUP BY a.id, a.nama, a.role, a.username
                
            """
            , nativeQuery = true)
    Page<AccountListDto>getAccountList(Pageable pageable);

    @Query(
            value = """
              Select new com.saktiform.api.model.account.AccountDropdownDto(
                acc.id, acc.username, acc.nama
              )
              FROM Account acc
              where acc.isDeleted != true and acc.role != "OWNER"
              """
    )
    List<AccountDropdownDto> getAccountDropdown();

    @Transactional
    @Modifying
    @Query("update Account p set p.isDeleted = true where p.id = :id")
    int updateIsDeletedById(@Param(":id") Long id);

    boolean existsByUsername(String username);
}
