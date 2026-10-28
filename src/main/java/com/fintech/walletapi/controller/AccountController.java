package com.fintech.walletapi.controller;

import com.fintech.walletapi.dto.AccountDTO;
import com.fintech.walletapi.dto.BalanceUpdateDTO;
import com.fintech.walletapi.dto.ApiResponse;
import com.fintech.walletapi.service.AccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "3. Wallets", description = "Peso and forex wallets")
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountDTO>> createAccount(@Valid @RequestBody AccountDTO newAccount) {
        AccountDTO savedAccount = accountService.createAccount(newAccount);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Account created successfully", savedAccount));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountDTO>>> getAllAccounts(
            @RequestParam(value = "status", required = false) String status) {
        List<AccountDTO> accounts = (status != null && !status.isBlank())
                ? accountService.getAccountByStatus(status)
                : accountService.getAllAccounts();
        return ResponseEntity.ok(new ApiResponse<>(200, "Successfully fetched accounts", accounts));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<AccountDTO>>> getMyAccounts(Authentication authentication) {
        List<AccountDTO> accounts = accountService.getMyAccounts(authentication.getName());
        return ResponseEntity.ok(new ApiResponse<>(200, "Successfully fetched your wallets", accounts));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountDTO>> getAccountById(@PathVariable("id") Long id) {
        AccountDTO account = accountService.getAccountById(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Account found", account));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountDTO>> updatedAccount(@PathVariable("id") Long id,
                                                                  @Valid @RequestBody BalanceUpdateDTO updatedAccount) {
        AccountDTO adjustment = new AccountDTO();
        adjustment.setBalance(updatedAccount.getBalance());
        AccountDTO updated = accountService.updateAccountBalance(id, adjustment);
        if (updated == null) {
            throw new IllegalArgumentException("Account to update not found with ID: " + id);
        }
        return ResponseEntity.ok(new ApiResponse<>(200, "Account updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> closeAccount(@PathVariable("id") Long id) {
        if (accountService.closeAccount(id)) {
            return ResponseEntity.ok(new ApiResponse<>(200, "Account closed successfully"));
        }
        throw new IllegalArgumentException("Account not found with ID: " + id);
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<Page<AccountDTO>>> getAccountsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        Page<AccountDTO> results = accountService.getPaginatedAccounts(page, size);
        return ResponseEntity.ok(new ApiResponse<>(200, "Page fetched", results));
    }
}
