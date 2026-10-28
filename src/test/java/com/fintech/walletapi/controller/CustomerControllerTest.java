package com.fintech.walletapi.controller;

import com.fintech.walletapi.dto.CustomerDTO;
import com.fintech.walletapi.security.CustomUserDetailsService;
import com.fintech.walletapi.security.JwtUtil;
import com.fintech.walletapi.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private static UsernamePasswordAuthenticationToken as(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }

    @Test
    @DisplayName("TC-CUSCTL-01 | CustomerController.getCurrentCustomer | logged-in alice -> 200 with her profile")
    void me_ok() throws Exception {
        when(customerService.getCurrentCustomer("alice"))
                .thenReturn(new CustomerDTO(3L, "Alice Johnson", "alice@example.com", "0917", "alice"));
        mockMvc.perform(get("/api/v1/customers/me").principal(as("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    @DisplayName("TC-CUSCTL-02 | CustomerController.getCurrentCustomer | user without profile -> 404")
    void me_noProfile() throws Exception {
        when(customerService.getCurrentCustomer("admin"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        mockMvc.perform(get("/api/v1/customers/me").principal(as("admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-CUSCTL-03 | CustomerController.getCurrentCustomer | username comes from the token")
    void me_usesToken() throws Exception {
        when(customerService.getCurrentCustomer("bob")).thenReturn(new CustomerDTO(4L, "Bob"));
        mockMvc.perform(get("/api/v1/customers/me").principal(as("bob")))
                .andExpect(jsonPath("$.message").value("Successfully retrieved customer"));
        verify(customerService).getCurrentCustomer("bob");
    }

    @Test
    @DisplayName("TC-CUSCTL-04 | CustomerController.createCustomer | valid body -> 201")
    void create_ok() throws Exception {
        when(customerService.createCustomer(any())).thenReturn(new CustomerDTO(9L, "Carol Cruz"));
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Carol Cruz\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(9));
    }

    @Test
    @DisplayName("TC-CUSCTL-05 | CustomerController.createCustomer | blank name -> 400 'Customer name is required'")
    void create_blankName() throws Exception {
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Customer name is required"));
    }

    @Test
    @DisplayName("TC-CUSCTL-06 | CustomerController.createCustomer | non-admin -> 403")
    void create_forbidden() throws Exception {
        when(customerService.createCustomer(any())).thenThrow(new AccessDeniedException("admin only"));
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-CUSCTL-07 | CustomerController.getAllCustomers | populated list -> 200 with profiles")
    void listCustomers() throws Exception {
        when(customerService.getAllCustomers()).thenReturn(List.of(new CustomerDTO(3L, "Alice")));
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(3));
    }
    @Test
    @DisplayName("TC-CUSCTL-08 | CustomerController.getAllCustomers | empty list -> empty JSON array")
    void listEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }
    @Test
    @DisplayName("TC-CUSCTL-09 | CustomerController.getAllCustomers | forbidden service -> 403")
    void listForbidden() throws Exception {
        when(customerService.getAllCustomers()).thenThrow(new org.springframework.security.access.AccessDeniedException("admin"));
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isForbidden());
    }
    @Test
    @DisplayName("TC-CUSCTL-10 | CustomerController.getCustomerById | existing id -> 200")
    void readCustomer() throws Exception {
        when(customerService.getCustomerById(3L)).thenReturn(new CustomerDTO(3L, "Alice"));
        mockMvc.perform(get("/api/v1/customers/3")).andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Alice"));
    }
    @Test
    @DisplayName("TC-CUSCTL-11 | CustomerController.getCustomerById | missing id -> 404")
    void readMissing() throws Exception {
        when(customerService.getCustomerById(99L)).thenThrow(new IllegalArgumentException("Customer not found"));
        mockMvc.perform(get("/api/v1/customers/99")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorType").value("Not Found"));
    }
    @Test
    @DisplayName("TC-CUSCTL-12 | CustomerController.getCustomerById | nonnumeric id -> 400")
    void readInvalidId() throws Exception { mockMvc.perform(get("/api/v1/customers/abc")).andExpect(status().isBadRequest()); }
    @Test
    @DisplayName("TC-CUSCTL-13 | CustomerController.updateCustomer | valid contact details -> 200")
    void updateCustomer() throws Exception {
        when(customerService.updateCustomer(eq(3L), any())).thenReturn(new CustomerDTO(3L, "Updated"));
        mockMvc.perform(put("/api/v1/customers/3").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Updated\",\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Updated"));
    }
    @Test
    @DisplayName("TC-CUSCTL-14 | CustomerController.updateCustomer | blank name -> 400")
    void updateBlankName() throws Exception {
        mockMvc.perform(put("/api/v1/customers/3").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest()); verify(customerService, never()).updateCustomer(any(), any());
    }
    @Test
    @DisplayName("TC-CUSCTL-15 | CustomerController.updateCustomer | invalid email -> 400")
    void updateInvalidEmail() throws Exception {
        mockMvc.perform(put("/api/v1/customers/3").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Alice\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest()); verify(customerService, never()).updateCustomer(any(), any());
    }
    @Test
    @DisplayName("TC-CUSCTL-16 | CustomerController.deleteCustomer | eligible customer -> 200")
    void deleteCustomer() throws Exception { mockMvc.perform(delete("/api/v1/customers/3")).andExpect(status().isOk()); verify(customerService).deleteCustomer(3L); }
    @Test
    @DisplayName("TC-CUSCTL-17 | CustomerController.deleteCustomer | wallets remain -> 409")
    void deleteConflict() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Wallets remain"))
                .when(customerService).deleteCustomer(3L);
        mockMvc.perform(delete("/api/v1/customers/3")).andExpect(status().isConflict()).andExpect(jsonPath("$.errorType").value("Conflict"));
    }
    @Test
    @DisplayName("TC-CUSCTL-18 | CustomerController.deleteCustomer | missing id -> 404")
    void deleteMissing() throws Exception {
        doThrow(new IllegalArgumentException("Customer not found")).when(customerService).deleteCustomer(99L);
        mockMvc.perform(delete("/api/v1/customers/99")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-CUSCTL-19 | CustomerController.createCustomer | phone '+639171234567' -> 201")
    void createWithInternationalPhone() throws Exception {
        when(customerService.createCustomer(any())).thenReturn(new CustomerDTO(9L, "Carol Cruz"));
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Carol Cruz\",\"phone\":\"+639171234567\"}"))
                .andExpect(status().isCreated());
        verify(customerService).createCustomer(any());
    }

    @Test
    @DisplayName("TC-CUSCTL-20 | CustomerController.createCustomer | phone 'abc' -> 400 with phone message, service not called")
    void createWithInvalidPhone() throws Exception {
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Carol Cruz\",\"phone\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone must be a Philippine mobile number such as 09171234567 or +639171234567"));
        verifyNoInteractions(customerService);
    }

    @Test
    @DisplayName("TC-CUSCTL-21 | CustomerController.updateCustomer | phone '09179999999' -> 200")
    void updateWithValidPhone() throws Exception {
        when(customerService.updateCustomer(eq(3L), any())).thenReturn(new CustomerDTO(3L, "Alice"));
        mockMvc.perform(put("/api/v1/customers/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Alice\",\"phone\":\"09179999999\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-CUSCTL-22 | CustomerController.updateCustomer | phone '091712345678' (12 digits) -> 400, service not called")
    void updateWithTooLongPhone() throws Exception {
        mockMvc.perform(put("/api/v1/customers/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Alice\",\"phone\":\"091712345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone must be a Philippine mobile number such as 09171234567 or +639171234567"));
        verifyNoInteractions(customerService);
    }
}

