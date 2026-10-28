package com.fintech.walletapi.controller;

import com.fintech.walletapi.dto.TransferRecordDTO;
import com.fintech.walletapi.security.CustomUserDetailsService;
import com.fintech.walletapi.security.JwtUtil;
import com.fintech.walletapi.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerTest {

    private static final Principal ALICE = new UsernamePasswordAuthenticationToken("alice", null, List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private static String body(String fromId, String toId, String amount) {
        return "{\"fromId\":" + fromId + ",\"toId\":" + toId + ",\"amount\":" + amount + "}";
    }

    @Test
    @DisplayName("TC-TRFCTL-01 | TransferController.transferMoney | valid body + JWT user -> 200 'Transfer successful', username from token")
    void transfer_success() throws Exception {
        when(transferService.transferMoney(eq(3L), eq(4L), argThat(a -> a != null && a.compareTo(new BigDecimal("100")) == 0), eq("alice")))
                .thenReturn("Successfully transferred 100.00 from Account #3 to Account #4");

        mockMvc.perform(post("/api/v1/transfers").principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content(body("3", "4", "100.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Transfer successful"))
                .andExpect(jsonPath("$.data").value("Successfully transferred 100.00 from Account #3 to Account #4"));
    }

    @Test
    @DisplayName("TC-TRFCTL-02 | TransferController.transferMoney | missing fromId -> 400 'Source account ID is required'")
    void transfer_missingFromId() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"toId\":4,\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Source account ID is required"));
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("TC-TRFCTL-03 | TransferController.transferMoney | amount 0 -> 400 'Transfer amount must be greater than zero'")
    void transfer_zeroAmount() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content(body("3", "4", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transfer amount must be greater than zero"));
    }

    @Test
    @DisplayName("TC-TRFCTL-04 | TransferController.transferMoney | missing toId -> 400 'Destination account ID is required'")
    void transfer_missingToId() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fromId\":3,\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Destination account ID is required"));
    }

    @Test
    @DisplayName("TC-TRFCTL-05 | TransferController.transferMoney | destination 999 missing -> 404 'Destination account not found'")
    void transfer_destinationMissing() throws Exception {
        when(transferService.transferMoney(eq(3L), eq(999L), any(), eq("alice")))
                .thenThrow(new IllegalArgumentException("Destination account not found"));

        mockMvc.perform(post("/api/v1/transfers").principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content(body("3", "999", "100.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Destination account not found"));
    }

    @Test
    @DisplayName("TC-TRFCTL-06 | TransferController.transferMoney | sender is not the owner -> 403")
    void transfer_notOwner() throws Exception {
        when(transferService.transferMoney(any(), any(), any(), anyString()))
                .thenThrow(new AccessDeniedException("You do not own this account"));

        mockMvc.perform(post("/api/v1/transfers").principal(ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content(body("3", "4", "1")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-TRFCTL-07 | TransferController.transferMoney | nobody logged in -> 401, service never called")
    void transfer_noUser() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(body("3", "4", "1")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("TC-TRFCTL-08 | TransferController.getTransferHistory | owner -> 200 with list of TransferRecordDTO")
    void history_success() throws Exception {
        TransferRecordDTO dto = new TransferRecordDTO();
        dto.setId(1L);
        dto.setSourceAccountId(3L);
        dto.setDestinationAccountId(4L);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setDirection("SENT");
        when(transferService.getTransferHistory(3L, "alice")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/transfers/history/3").principal(ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceAccountId").value(3))
                .andExpect(jsonPath("$.data[0].destinationAccountId").value(4))
                .andExpect(jsonPath("$.data[0].amount").value(100.00))
                .andExpect(jsonPath("$.data[0].direction").value("SENT"));
    }

    @Test
    @DisplayName("TC-TRFCTL-09 | TransferController.getTransferHistory | not the owner -> 403")
    void history_forbidden() throws Exception {
        when(transferService.getTransferHistory(3L, "alice")).thenThrow(new AccessDeniedException("no"));
        mockMvc.perform(get("/api/v1/transfers/history/3").principal(ALICE))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-TRFCTL-10 | TransferController.getTransferHistory | unknown account -> 404 'Account not found'")
    void history_missing() throws Exception {
        when(transferService.getTransferHistory(99L, "alice")).thenThrow(new IllegalArgumentException("Account not found"));
        mockMvc.perform(get("/api/v1/transfers/history/99").principal(ALICE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    @DisplayName("TC-TRFCTL-11 | TransferController.getTransferHistory | non-numeric id 'abc' -> 400")
    void history_badId() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/history/abc").principal(ALICE))
                .andExpect(status().isBadRequest());
    }
}
