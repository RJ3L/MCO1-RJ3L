package com.fintech.walletapi.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}: each handler is called directly.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * Builds a mocked validation exception that reports one field error.
     *
     * <p><b>Pre-condition:</b> None.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param fieldError the error to report, or {@code null}
     * @return the mocked exception
     */
    private static MethodArgumentNotValidException validationError(FieldError fieldError) {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldError()).thenReturn(fieldError);
        return ex;
    }

    /**
     * TC-GEH-01: tests {@code GlobalExceptionHandler.handleValidationErrors}.
     *
     * <p><b>Pre-condition:</b> Amount field error.</p>
     * <p><b>Post-condition:</b> 400 with DTO message.</p>
     */
    @Test
    @DisplayName("TC-GEH-01 | GlobalExceptionHandler.handleValidationErrors | amount field error -> 400 with DTO message")
    void validation_amount() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleValidationErrors(validationError(
                new FieldError("transfer", "amount", "Transfer amount must be greater than zero")));
        assertEquals(400, res.getStatusCode().value());
        assertEquals("Transfer amount must be greater than zero", res.getBody().getMessage());
    }

    /**
     * TC-GEH-02: tests {@code GlobalExceptionHandler.handleValidationErrors}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> ErrorType is 'Validation Failed'.</p>
     */
    @Test
    @DisplayName("TC-GEH-02 | GlobalExceptionHandler.handleValidationErrors | errorType is 'Validation Failed'")
    void validation_errorType() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleValidationErrors(validationError(
                new FieldError("register", "email", "Email must be a valid email address")));
        assertEquals("Validation Failed", res.getBody().getErrorType());
        assertEquals(400, res.getBody().getStatus());
    }

    /**
     * TC-GEH-03: tests {@code GlobalExceptionHandler.handleValidationErrors}.
     *
     * <p><b>Pre-condition:</b> No field error (object-level).</p>
     * <p><b>Post-condition:</b> 'Invalid request'.</p>
     */
    @Test
    @DisplayName("TC-GEH-03 | GlobalExceptionHandler.handleValidationErrors | no field error (object-level) -> 'Invalid request'")
    void validation_noFieldError() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleValidationErrors(validationError(null));
        assertEquals("Invalid request", res.getBody().getMessage());
    }

    /**
     * TC-GEH-04: tests {@code GlobalExceptionHandler.handleNotFound}.
     *
     * <p><b>Pre-condition:</b> 'Destination account not found'.</p>
     * <p><b>Post-condition:</b> 404 same message.</p>
     */
    @Test
    @DisplayName("TC-GEH-04 | GlobalExceptionHandler.handleNotFound | 'Destination account not found' -> 404 same message")
    void notFound_destination() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleNotFound(
                new IllegalArgumentException("Destination account not found"));
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertEquals("Destination account not found", res.getBody().getMessage());
    }

    /**
     * TC-GEH-05: tests {@code GlobalExceptionHandler.handleNotFound}.
     *
     * <p><b>Pre-condition:</b> 'Insufficient funds'.</p>
     * <p><b>Post-condition:</b> 404 (course contract).</p>
     */
    @Test
    @DisplayName("TC-GEH-05 | GlobalExceptionHandler.handleNotFound | 'Insufficient funds' -> 404 (course contract)")
    void notFound_insufficientFunds() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleNotFound(new IllegalArgumentException("Insufficient funds"));
        assertEquals(404, res.getBody().getStatus());
    }

    /**
     * TC-GEH-06: tests {@code GlobalExceptionHandler.handleNotFound}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> ErrorType is 'Not Found'.</p>
     */
    @Test
    @DisplayName("TC-GEH-06 | GlobalExceptionHandler.handleNotFound | errorType is 'Not Found'")
    void notFound_errorType() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleNotFound(new IllegalArgumentException("x"));
        assertEquals("Not Found", res.getBody().getErrorType());
    }

    /**
     * TC-GEH-07: tests {@code GlobalExceptionHandler.handleInvalidTransaction}.
     *
     * <p><b>Pre-condition:</b> Same-account transfer.</p>
     * <p><b>Post-condition:</b> 400 same message.</p>
     */
    @Test
    @DisplayName("TC-GEH-07 | GlobalExceptionHandler.handleInvalidTransaction | same-account transfer -> 400 same message")
    void invalid_sameAccount() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleInvalidTransaction(
                new InvalidTransactionException("Cannot transfer money to the same account"));
        assertEquals(400, res.getStatusCode().value());
        assertEquals("Cannot transfer money to the same account", res.getBody().getMessage());
    }

    /**
     * TC-GEH-08: tests {@code GlobalExceptionHandler.handleInvalidTransaction}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> ErrorType is 'Invalid Transaction'.</p>
     */
    @Test
    @DisplayName("TC-GEH-08 | GlobalExceptionHandler.handleInvalidTransaction | errorType is 'Invalid Transaction'")
    void invalid_errorType() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleInvalidTransaction(new InvalidTransactionException("x"));
        assertEquals("Invalid Transaction", res.getBody().getErrorType());
    }

    /**
     * TC-GEH-09: tests {@code GlobalExceptionHandler.handleInvalidTransaction}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Unsupported currency message passed through.</p>
     */
    @Test
    @DisplayName("TC-GEH-09 | GlobalExceptionHandler.handleInvalidTransaction | unsupported currency message passed through")
    void invalid_currency() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleInvalidTransaction(
                new InvalidTransactionException("Unsupported currency: JPY"));
        assertTrue(res.getBody().getMessage().contains("JPY"));
    }

    /**
     * TC-GEH-10: tests {@code GlobalExceptionHandler.handleBadCredentials}.
     *
     * <p><b>Pre-condition:</b> Bad password.</p>
     * <p><b>Post-condition:</b> 401.</p>
     */
    @Test
    @DisplayName("TC-GEH-10 | GlobalExceptionHandler.handleBadCredentials | bad password -> 401")
    void badCredentials_status() {
        assertEquals(401, handler.handleBadCredentials(new BadCredentialsException("x")).getStatusCode().value());
    }

    /**
     * TC-GEH-11: tests {@code GlobalExceptionHandler.handleBadCredentials}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Message never reveals which field was wrong.</p>
     */
    @Test
    @DisplayName("TC-GEH-11 | GlobalExceptionHandler.handleBadCredentials | message never reveals which field was wrong")
    void badCredentials_message() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleBadCredentials(new BadCredentialsException("User alice not found"));
        assertEquals("Invalid username or password", res.getBody().getMessage());
    }

    /**
     * TC-GEH-12: tests {@code GlobalExceptionHandler.handleBadCredentials}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> ErrorType is 'Unauthorized'.</p>
     */
    @Test
    @DisplayName("TC-GEH-12 | GlobalExceptionHandler.handleBadCredentials | errorType is 'Unauthorized'")
    void badCredentials_errorType() {
        assertEquals("Unauthorized", handler.handleBadCredentials(new BadCredentialsException("x")).getBody().getErrorType());
    }

    /**
     * TC-GEH-13: tests {@code GlobalExceptionHandler.handleAccessDenied}.
     *
     * <p><b>Pre-condition:</b> Not the owner.</p>
     * <p><b>Post-condition:</b> 403.</p>
     */
    @Test
    @DisplayName("TC-GEH-13 | GlobalExceptionHandler.handleAccessDenied | not the owner -> 403")
    void accessDenied_status() {
        assertEquals(403, handler.handleAccessDenied(new AccessDeniedException("x")).getStatusCode().value());
    }

    /**
     * TC-GEH-14: tests {@code GlobalExceptionHandler.handleAccessDenied}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Generic permission message.</p>
     */
    @Test
    @DisplayName("TC-GEH-14 | GlobalExceptionHandler.handleAccessDenied | generic permission message")
    void accessDenied_message() {
        assertEquals("You do not have permission to access this resource",
                handler.handleAccessDenied(new AccessDeniedException("You do not own this account")).getBody().getMessage());
    }

    /**
     * TC-GEH-15: tests {@code GlobalExceptionHandler.handleAccessDenied}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> ErrorType is 'Forbidden'.</p>
     */
    @Test
    @DisplayName("TC-GEH-15 | GlobalExceptionHandler.handleAccessDenied | errorType is 'Forbidden'")
    void accessDenied_errorType() {
        assertEquals("Forbidden", handler.handleAccessDenied(new AccessDeniedException("x")).getBody().getErrorType());
    }

    /**
     * TC-GEH-16: tests {@code GlobalExceptionHandler.handleBadRequest}.
     *
     * <p><b>Pre-condition:</b> Malformed JSON body.</p>
     * <p><b>Post-condition:</b> 400.</p>
     */
    @Test
    @DisplayName("TC-GEH-16 | GlobalExceptionHandler.handleBadRequest | malformed JSON body -> 400")
    void badRequest_json() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleBadRequest(mock(HttpMessageNotReadableException.class));
        assertEquals(400, res.getStatusCode().value());
    }

    /**
     * TC-GEH-17: tests {@code GlobalExceptionHandler.handleBadRequest}.
     *
     * <p><b>Pre-condition:</b> Non-numeric id in URL.</p>
     * <p><b>Post-condition:</b> 400.</p>
     */
    @Test
    @DisplayName("TC-GEH-17 | GlobalExceptionHandler.handleBadRequest | non-numeric id in URL -> 400")
    void badRequest_typeMismatch() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleBadRequest(mock(MethodArgumentTypeMismatchException.class));
        assertEquals(400, res.getStatusCode().value());
    }

    /**
     * TC-GEH-18: tests {@code GlobalExceptionHandler.handleBadRequest}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Fixed safe message.</p>
     */
    @Test
    @DisplayName("TC-GEH-18 | GlobalExceptionHandler.handleBadRequest | fixed safe message")
    void badRequest_message() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleBadRequest(mock(HttpMessageNotReadableException.class));
        assertEquals("Malformed JSON request or invalid value in the URL", res.getBody().getMessage());
    }

    /**
     * TC-GEH-19: tests {@code GlobalExceptionHandler.handleEverythingElse}.
     *
     * <p><b>Pre-condition:</b> Spring web error (404 status exception).</p>
     * <p><b>Post-condition:</b> Keeps 404.</p>
     */
    @Test
    @DisplayName("TC-GEH-19 | GlobalExceptionHandler.handleEverythingElse | Spring web error (404 status exception) -> keeps 404")
    void everythingElse_webError() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleEverythingElse(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        assertEquals(404, res.getStatusCode().value());
    }

    /**
     * TC-GEH-20: tests {@code GlobalExceptionHandler.handleEverythingElse}.
     *
     * <p><b>Pre-condition:</b> Authentication problem.</p>
     * <p><b>Post-condition:</b> 401.</p>
     */
    @Test
    @DisplayName("TC-GEH-20 | GlobalExceptionHandler.handleEverythingElse | authentication problem -> 401")
    void everythingElse_auth() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleEverythingElse(
                new AuthenticationCredentialsNotFoundException("no user"));
        assertEquals(401, res.getStatusCode().value());
    }

    /**
     * TC-GEH-21: tests {@code GlobalExceptionHandler.handleEverythingElse}.
     *
     * <p><b>Pre-condition:</b> Unexpected bug.</p>
     * <p><b>Post-condition:</b> 500 with safe message.</p>
     */
    @Test
    @DisplayName("TC-GEH-21 | GlobalExceptionHandler.handleEverythingElse | unexpected bug -> 500 with safe message")
    void everythingElse_bug() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleEverythingElse(new NullPointerException("secret detail"));
        assertEquals(500, res.getStatusCode().value());
        assertFalse(res.getBody().getMessage().contains("secret"));
    }

    private static final ForexProviderUnavailableException PROVIDER_DOWN =
            new ForexProviderUnavailableException("Live exchange rates are unavailable right now");

    /**
     * TC-GEH-22: tests {@code GlobalExceptionHandler.handleProviderUnavailable}.
     *
     * <p><b>Pre-condition:</b> Live-rate provider down.</p>
     * <p><b>Post-condition:</b> 503.</p>
     */
    @Test
    @DisplayName("TC-GEH-22 | GlobalExceptionHandler.handleProviderUnavailable | live-rate provider down -> 503")
    void providerUnavailable_status() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleProviderUnavailable(PROVIDER_DOWN);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, res.getStatusCode());
        assertEquals(503, res.getBody().getStatus());
    }

    /**
     * TC-GEH-23: tests {@code GlobalExceptionHandler.handleProviderUnavailable}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> ErrorType is 'Service Unavailable'.</p>
     */
    @Test
    @DisplayName("TC-GEH-23 | GlobalExceptionHandler.handleProviderUnavailable | errorType is 'Service Unavailable'")
    void providerUnavailable_errorType() {
        assertEquals("Service Unavailable", handler.handleProviderUnavailable(PROVIDER_DOWN).getBody().getErrorType());
    }

    /**
     * TC-GEH-24: tests {@code GlobalExceptionHandler.handleProviderUnavailable}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Exception message passed to the client.</p>
     */
    @Test
    @DisplayName("TC-GEH-24 | GlobalExceptionHandler.handleProviderUnavailable | exception message passed to the client")
    void providerUnavailable_message() {
        assertEquals("Live exchange rates are unavailable right now",
                handler.handleProviderUnavailable(PROVIDER_DOWN).getBody().getMessage());
    }

    private static final DuplicateWalletException DUPLICATE_WALLET =
            new DuplicateWalletException("Customer 5 already has a USD wallet");

    /**
     * TC-GEH-25: tests {@code GlobalExceptionHandler.handleDuplicateWallet}.
     *
     * <p><b>Pre-condition:</b> Second USD wallet.</p>
     * <p><b>Post-condition:</b> 409.</p>
     */
    @Test
    @DisplayName("TC-GEH-25 | GlobalExceptionHandler.handleDuplicateWallet | second USD wallet -> 409")
    void duplicateWallet_status() {
        ResponseEntity<ErrorResponseDTO> res = handler.handleDuplicateWallet(DUPLICATE_WALLET);
        assertEquals(HttpStatus.CONFLICT, res.getStatusCode());
        assertEquals(409, res.getBody().getStatus());
    }

    /**
     * TC-GEH-26: tests {@code GlobalExceptionHandler.handleDuplicateWallet}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> ErrorType is 'Conflict'.</p>
     */
    @Test
    @DisplayName("TC-GEH-26 | GlobalExceptionHandler.handleDuplicateWallet | errorType is 'Conflict'")
    void duplicateWallet_errorType() {
        assertEquals("Conflict", handler.handleDuplicateWallet(DUPLICATE_WALLET).getBody().getErrorType());
    }

    /**
     * TC-GEH-27: tests {@code GlobalExceptionHandler.handleDuplicateWallet}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Exception message passed to the client.</p>
     */
    @Test
    @DisplayName("TC-GEH-27 | GlobalExceptionHandler.handleDuplicateWallet | exception message passed to the client")
    void duplicateWallet_message() {
        assertEquals("Customer 5 already has a USD wallet",
                handler.handleDuplicateWallet(DUPLICATE_WALLET).getBody().getMessage());
    }

    private static final DataIntegrityViolationException CONSTRAINT_HIT = new DataIntegrityViolationException(
            "could not execute statement [Duplicate entry '5-USD' for key 'uk_accounts_customer_currency']");

    /**
     * TC-GEH-28: tests {@code GlobalExceptionHandler.handleDataIntegrityViolation}.
     *
     * <p><b>Pre-condition:</b> Unique key rejects a write.</p>
     * <p><b>Post-condition:</b> 409.</p>
     */
    @Test
    @DisplayName("TC-GEH-28 | GlobalExceptionHandler.handleDataIntegrityViolation | unique key rejects a write -> 409")
    void dataIntegrity_status() {
        assertEquals(409, handler.handleDataIntegrityViolation(CONSTRAINT_HIT).getStatusCode().value());
    }

    /**
     * TC-GEH-29: tests {@code GlobalExceptionHandler.handleDataIntegrityViolation}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> ErrorType is 'Conflict'.</p>
     */
    @Test
    @DisplayName("TC-GEH-29 | GlobalExceptionHandler.handleDataIntegrityViolation | errorType is 'Conflict'")
    void dataIntegrity_errorType() {
        assertEquals("Conflict", handler.handleDataIntegrityViolation(CONSTRAINT_HIT).getBody().getErrorType());
    }

    /**
     * TC-GEH-30: tests {@code GlobalExceptionHandler.handleDataIntegrityViolation}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> SQL details are not leaked to the client.</p>
     */
    @Test
    @DisplayName("TC-GEH-30 | GlobalExceptionHandler.handleDataIntegrityViolation | SQL details are not leaked to the client")
    void dataIntegrity_safeMessage() {
        String message = handler.handleDataIntegrityViolation(CONSTRAINT_HIT).getBody().getMessage();
        assertEquals("The request conflicts with existing data", message);
        assertFalse(message.contains("uk_accounts_customer_currency"));
    }

    /**
     * TC-GEH-40: tests {@code GlobalExceptionHandler.handleConcurrentUpdate}.
     *
     * <p><b>Pre-condition:</b> Stale update.</p>
     * <p><b>Post-condition:</b> 409.</p>
     */
    @Test
    @DisplayName("TC-GEH-40 | GlobalExceptionHandler.handleConcurrentUpdate | stale update -> 409")
    void concurrentUpdateStatus() {
        assertEquals(409, handler.handleConcurrentUpdate(new org.springframework.dao.OptimisticLockingFailureException("stale")).getStatusCode().value());
    }
    /**
     * TC-GEH-41: tests {@code GlobalExceptionHandler.handleConcurrentUpdate}.
     *
     * <p><b>Pre-condition:</b> Conflict body.</p>
     * <p><b>Post-condition:</b> Consistent error type.</p>
     */
    @Test
    @DisplayName("TC-GEH-41 | GlobalExceptionHandler.handleConcurrentUpdate | conflict body -> consistent error type")
    void concurrentUpdateShape() {
        assertEquals("Conflict", handler.handleConcurrentUpdate(new org.springframework.dao.OptimisticLockingFailureException("stale")).getBody().getErrorType());
    }
    /**
     * TC-GEH-42: tests {@code GlobalExceptionHandler.handleConcurrentUpdate}.
     *
     * <p><b>Pre-condition:</b> Database details.</p>
     * <p><b>Post-condition:</b> Not exposed.</p>
     */
    @Test
    @DisplayName("TC-GEH-42 | GlobalExceptionHandler.handleConcurrentUpdate | database details -> not exposed")
    void concurrentUpdateSafeMessage() {
        assertFalse(handler.handleConcurrentUpdate(new org.springframework.dao.OptimisticLockingFailureException("secret SQL")).getBody().getMessage().contains("secret"));
    }

}
