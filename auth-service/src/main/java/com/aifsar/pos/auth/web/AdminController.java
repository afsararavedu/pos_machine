package com.aifsar.pos.auth.web;

import java.util.List;
import java.util.Set;

import com.aifsar.pos.auth.domain.Role;
import com.aifsar.pos.auth.domain.UserAccount;
import com.aifsar.pos.auth.domain.UserAccount.AccountType;
import com.aifsar.pos.auth.service.UserAdminService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-only (enforced in SecurityConfig for /auth/admin/**). */
@RestController
@RequestMapping("/auth/admin/users")
public class AdminController {

    /** Password policy: 8+ chars with upper, lower, digit and special character. */
    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String username,
            @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,128}$",
                    message = "must be 8+ chars with upper, lower, digit and special character") String password,
            @NotNull AccountType type,
            @NotEmpty Set<Role> roles,
            String merchantId,
            String terminalId) {
    }

    public record UserView(String username, AccountType type, List<String> roles, String merchantId,
                           String terminalId, boolean enabled) {
        static UserView of(UserAccount a) {
            return new UserView(a.getUsername(), a.getAccountType(), a.roleList(), a.getMerchantId(),
                    a.getTerminalId(), a.isEnabled());
        }
    }

    private final UserAdminService admin;

    public AdminController(UserAdminService admin) {
        this.admin = admin;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUserRequest r) {
        return UserView.of(admin.create(r.username(), r.password(), r.type(), r.roles(), r.merchantId(),
                r.terminalId()));
    }

    @PostMapping("/{username}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable String username) {
        admin.unlock(username);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{username}/disable")
    public ResponseEntity<Void> disable(@PathVariable String username) {
        admin.disable(username);
        return ResponseEntity.noContent().build();
    }
}
