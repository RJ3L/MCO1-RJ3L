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

/**
 * Wallet (account) endpoints ({@code /api/v1/accounts}).
 */
@RestController
@Tag(name = "3. Wallets", description = "Peso and forex wallets")
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    /**
     * Creates the controller.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param accountService wallet business logic
     */
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * {@code POST /api/v1/accounts} — opens a peso or forex wallet.
     *
     * <p><b>Pre-condition:</b> body passes validation; caller is an ADMIN or owns {@code customerId}.</p>
     * <p><b>Post-condition:</b> a new wallet exists.</p>
     *
     * @param newAccount account type, opening balance, status, customerId and optional currency
     * @return 201 with the saved wallet; 409 if the customer already has a wallet in that currency
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AccountDTO>> createAccount(@Valid @RequestBody AccountDTO newAccount) {
        AccountDTO savedAccount = accountService.createAccount(newAccount);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Account created successfully", savedAccount));
    }

    /**
     * {@code GET /api/v1/accounts[?status=ACTIVE]} — lists all wallets (admin).
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param status optional status filter
     * @return 200 with the wallets
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountDTO>>> getAllAccounts(
            @RequestParam(value = "status", required = false) String status) {
        List<AccountDTO> accounts = (status != null && !status.isBlank())
                ? accountService.getAccountByStatus(status)
                : accountService.getAllAccounts();
        return ResponseEntity.ok(new ApiResponse<>(200, "Successfully fetched accounts", accounts));
    }

    /**
     * {@code GET /api/v1/accounts/mine} — the logged-in user's own wallets.
     *
     * <p><b>Pre-condition:</b> a valid JWT. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param authentication the logged-in user, injected from the JWT
     * @return 200 with the user's wallets
     */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<AccountDTO>>> getMyAccounts(Authentication authentication) {
        List<AccountDTO> accounts = accountService.getMyAccounts(authentication.getName());
        return ResponseEntity.ok(new ApiResponse<>(200, "Successfully fetched your wallets", accounts));
    }

    /**
     * {@code GET /api/v1/accounts/{id}} — one wallet (owner or admin).
     *
     * <p><b>Pre-condition:</b> a valid JWT. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param id the account id
     * @return 200 with the wallet; 404 if missing; 403 if not the owner
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountDTO>> getAccountById(@PathVariable("id") Long id) {
        AccountDTO account = accountService.getAccountById(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Account found", account));
    }

    /**
     * {@code PUT /api/v1/accounts/{id}} — admin balance adjustment.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN.</p>
     * <p><b>Post-condition:</b> the wallet's balance equals the new value.</p>
     *
     * @param id             the account id
     * @param updatedAccount carries the new balance
     * @return 200 with the updated wallet, or 404 if missing
     */
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

    /**
     * {@code DELETE /api/v1/accounts/{id}} — admin closes a wallet.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN.</p>
     * <p><b>Post-condition:</b> the wallet no longer exists.</p>
     *
     * @param id the account id
     * @return 200 if closed, 404 if missing
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> closeAccount(@PathVariable("id") Long id) {
        if (accountService.closeAccount(id)) {
            return ResponseEntity.ok(new ApiResponse<>(200, "Account closed successfully"));
        }
        throw new IllegalArgumentException("Account not found with ID: " + id);
    }

    /**
     * {@code GET /api/v1/accounts/page?page=0&size=5} — paginated wallets (admin).
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param page zero-based page number (default 0)
     * @param size wallets per page (default 5)
     * @return 200 with the page
     */
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<Page<AccountDTO>>> getAccountsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        Page<AccountDTO> results = accountService.getPaginatedAccounts(page, size);
        return ResponseEntity.ok(new ApiResponse<>(200, "Page fetched", results));
    }
}
