package com.aifsar.pos.auth.service;

import java.util.Set;

import com.aifsar.pos.auth.domain.Role;
import com.aifsar.pos.auth.domain.UserAccount.AccountType;
import com.aifsar.pos.auth.domain.UserAccountRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Demo accounts for local/dev. Disable with AUTH_SEED_ENABLED=false in real environments
 * and create accounts through the admin API instead.
 */
@Component
@ConditionalOnProperty(prefix = "auth.seed", name = "enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserAccountRepository users;
    private final UserAdminService admin;

    public DataSeeder(UserAccountRepository users, UserAdminService admin) {
        this.users = users;
        this.admin = admin;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("admin", "Admin@123", AccountType.USER, Set.of(Role.ADMIN), null, null);
        seed("auditor", "Auditor@123", AccountType.USER, Set.of(Role.AUDITOR), null, null);
        seed("merchant1", "Merchant@123", AccountType.USER, Set.of(Role.MERCHANT), "M1001", null);
        seed("merchant2", "Merchant@123", AccountType.USER, Set.of(Role.MERCHANT), "M1002", null);
        seed("T0001", "Terminal@123", AccountType.TERMINAL, Set.of(Role.TERMINAL), "M1001", "T0001");
        seed("T0002", "Terminal@123", AccountType.TERMINAL, Set.of(Role.TERMINAL), "M1002", "T0002");
    }

    private void seed(String username, String secret, AccountType type, Set<Role> roles, String merchantId,
                      String terminalId) {
        if (!users.existsByUsername(username)) {
            admin.create(username, secret, type, roles, merchantId, terminalId);
            log.info("Seeded demo account {}", username);
        }
    }
}
