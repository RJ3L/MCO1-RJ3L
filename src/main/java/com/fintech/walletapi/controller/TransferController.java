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

@RestController
@Tag(name = "5. Transfers", description = "Send money and read the ledger")
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> transferMoney(@Valid @RequestBody TransferRequestDTO request,
                                                             Principal principal) {
        String result = transferService.transferMoney(request.getFromId(), request.getToId(),
                request.getAmount(), resolveUsername(principal));
        return ResponseEntity.ok(new ApiResponse<>(200, "Transfer successful", result));
    }

    @GetMapping("/history/{accountId}")
    public ResponseEntity<ApiResponse<List<TransferRecordDTO>>> getTransferHistory(
            @PathVariable("accountId") Long accountId, Principal principal) {
        List<TransferRecordDTO> history = transferService.getTransferHistory(accountId, resolveUsername(principal));
        return ResponseEntity.ok(new ApiResponse<>(200, "Transfer history retrieved", history));
    }

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
