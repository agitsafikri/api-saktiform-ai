package com.saktiform.api.util;

import com.saktiform.api.entity.Account;
import com.saktiform.api.model.Role;
import com.saktiform.api.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.superadmin.username}")
    private String username;

    @Value("${app.superadmin.password}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (accountRepository.existsByUsername(username)) return;

        Account user = new Account();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNama("Super Admin");
        user.setRole(Role.ADMIN);

        accountRepository.save(user);
    }
}

