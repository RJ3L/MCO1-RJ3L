package com.fintech.walletapi.controller;

import com.fintech.walletapi.dto.ApiResponse;
import com.fintech.walletapi.dto.TransferRecordDTO;
import com.fintech.walletapi.dto.TransferRequestDTO;
import com.fintech.walletapi.service.TransferService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Money transfer and ledger endpoints ({@code /api/v1/transfers}).
 *
 * <p>The controller is deliberately thin: it reads the request, takes the
 * username from the JWT (never from the body), calls {@link TransferService}
 * and wraps the result in an {@link ApiResponse}.</p>
 */
@RestController
@Tag(name = "5. Transfers", description = "Send money and read the ledger")
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    /**
     * Creates the controller.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param transferService transfer engine
     */
    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * {@code POST /api/v1/transfers} — sends money between wallets (with forex conversion if needed).
     *
     * <p><b>Pre-condition:</b> a valid JWT; the body passes validation.</p>
     * <p><b>Post-condition:</b> on 200 both balances and the ledger were updated atomically;
     * on any error nothing changed.</p>
     *
     * @param request   fromId, toId and amount (in the source currency)
     * @param principal the logged-in user, filled in by Spring from the JWT
     * @return 200 with message "Transfer successful" and the service's result string in {@code data}
     */
    @PostMapping
    public ResponseEntity<ApiResponse<String>> transferMoney(@Valid @RequestBody TransferRequestDTO request,
                                                             Principal principal) {
        String result = transferService.transferMoney(request.getFromId(), request.getToId(),
                request.getAmount(), resolveUsername(principal));
        return ResponseEntity.ok(new ApiResponse<>(200, "Transfer successful", result));
    }

    /**
     * {@code GET /api/v1/transfers/history/{accountId}} — a wallet's ledger, newest first.
     *
     * <p><b>Pre-condition:</b> a valid JWT; the caller owns the account.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param accountId the wallet id
     * @param principal the logged-in user, filled in by Spring from the JWT
     * @return 200 with the list of {@link TransferRecordDTO}; 404 if missing; 403 if not the owner
     */
    @GetMapping("/history/{accountId}")
    public ResponseEntity<ApiResponse<List<TransferRecordDTO>>> getTransferHistory(
            @PathVariable("accountId") Long accountId, Principal principal) {
        List<TransferRecordDTO> history = transferService.getTransferHistory(accountId, resolveUsername(principal));
        return ResponseEntity.ok(new ApiResponse<>(200, "Transfer history retrieved", history));
    }

    /**
     * Gets the logged-in username from the request principal, falling back to the security context.
     *
     * <p><b>Pre-condition:</b> none.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param principal the request principal (may be null in some test setups)
     * @return the username
     * @throws AuthenticationCredentialsNotFoundException if nobody is logged in (401)
     */
    private String resolveUsername(Principal principal) {
        if (principal != null && principal.getName() != null) {
            return principal.getName();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return auth.getName();
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }
}
