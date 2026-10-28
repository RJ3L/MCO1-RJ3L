package com.fintech.walletapi.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Central error handling for every controller.
 *
 * <p>Each handler turns one kind of exception into a consistent
 * {@link ErrorResponseDTO} with the right HTTP status:</p>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} &rarr; 400 (bean validation)</li>
 *   <li>{@link InvalidTransactionException} &rarr; 400 (business rule)</li>
 *   <li>{@link HttpMessageNotReadableException} / {@link MethodArgumentTypeMismatchException} &rarr; 400</li>
 *   <li>{@link BadCredentialsException} &rarr; 401</li>
 *   <li>{@link AccessDeniedException} &rarr; 403 (not the owner / not an admin)</li>
 *   <li>{@link IllegalArgumentException} &rarr; 404 (resource not found, insufficient funds)</li>
 *   <li>{@link DuplicateWalletException} / {@link DataIntegrityViolationException} &rarr; 409
 *       (second wallet in the same currency, or another database constraint)</li>
 *   <li>{@link ForexProviderUnavailableException} &rarr; 503 (live-rate provider down)</li>
 *   <li>anything else &rarr; its own status, 401, or a safe 500</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles failed {@code @Valid} checks on request bodies.
     *
     * <p><b>Pre-condition:</b> a request DTO failed bean validation.</p>
     * <p><b>Post-condition:</b> a 400 response carrying the first validation message is built.</p>
     *
     * @param ex the validation exception raised by Spring MVC
     * @return 400 with {@code errorType = "Validation Failed"}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationErrors(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String errorMessage = fieldError != null ? fieldError.getDefaultMessage() : "Invalid request";
        ErrorResponseDTO error = new ErrorResponseDTO(400, "Validation Failed", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles "not found" situations and insufficient funds.
     *
     * <p><b>Pre-condition:</b> a service threw {@link IllegalArgumentException}.</p>
     * <p><b>Post-condition:</b> a 404 response with the exception message is built.</p>
     *
     * @param ex the exception (its message is shown to the client)
     * @return 404 with {@code errorType = "Not Found"}
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(IllegalArgumentException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handles broken business rules such as an unsupported currency or a self-transfer.
     *
     * <p><b>Pre-condition:</b> a service threw {@link InvalidTransactionException}.</p>
     * <p><b>Post-condition:</b> a 400 response with the exception message is built.</p>
     *
     * @param ex the exception (its message is shown to the client)
     * @return 400 with {@code errorType = "Invalid Transaction"}
     */
    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidTransaction(InvalidTransactionException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(), "Invalid Transaction", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles an attempt to open a second wallet in a currency the customer already holds.
     *
     * <p><b>Pre-condition:</b> the account service threw {@link DuplicateWalletException}.</p>
     * <p><b>Post-condition:</b> a 409 response with the exception message is built.</p>
     *
     * @param ex the exception (its message is shown to the client)
     * @return 409 with {@code errorType = "Conflict"}
     */
    @ExceptionHandler(DuplicateWalletException.class)
    public ResponseEntity<ErrorResponseDTO> handleDuplicateWallet(DuplicateWalletException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handles a write rejected by a database constraint, such as two simultaneous requests
     * opening the same-currency wallet for one customer (the unique key catches the second).
     *
     * <p><b>Pre-condition:</b> the database refused an insert or update.</p>
     * <p><b>Post-condition:</b> a 409 response is built without exposing SQL details.</p>
     *
     * @param ex the exception thrown by Spring Data / Hibernate
     * @return 409 with {@code errorType = "Conflict"}
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.CONFLICT.value(), "Conflict", "The request conflicts with existing data");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handles a live-rate sync that could not reach the external provider.
     *
     * <p><b>Pre-condition:</b> a sync threw {@link ForexProviderUnavailableException}.</p>
     * <p><b>Post-condition:</b> a 503 response with the exception message is built.</p>
     *
     * @param ex the exception (its message is shown to the client)
     * @return 503 with {@code errorType = "Service Unavailable"}
     */
    @ExceptionHandler(ForexProviderUnavailableException.class)
    public ResponseEntity<ErrorResponseDTO> handleProviderUnavailable(ForexProviderUnavailableException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.SERVICE_UNAVAILABLE.value(), "Service Unavailable", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    /**
     * Handles a wrong username or password on login.
     *
     * <p><b>Pre-condition:</b> authentication failed with bad credentials.</p>
     * <p><b>Post-condition:</b> a 401 response is built without revealing which field was wrong.</p>
     *
     * @param ex the exception thrown by the {@code AuthenticationManager}
     * @return 401 with the message "Invalid username or password"
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleBadCredentials(BadCredentialsException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Invalid username or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handles a logged-in user touching something they do not own or are not allowed to do.
     *
     * <p><b>Pre-condition:</b> method security or an ownership check threw {@link AccessDeniedException}.</p>
     * <p><b>Post-condition:</b> a 403 response is built.</p>
     *
     * @param ex the exception
     * @return 403 with a generic permission message
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.FORBIDDEN.value(), "Forbidden",
                "You do not have permission to access this resource");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handles unreadable JSON bodies and wrongly-typed URL values (e.g. {@code /accounts/abc}).
     *
     * <p><b>Pre-condition:</b> Spring could not read the body or convert a path/query value.</p>
     * <p><b>Post-condition:</b> a 400 response is built.</p>
     *
     * @param ex the exception
     * @return 400 with {@code errorType = "Bad Request"}
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDTO> handleBadRequest(Exception ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(), "Bad Request",
                "Malformed JSON request or invalid value in the URL");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Reports a rejected stale write without exposing persistence details.
     * <p><b>Pre-condition:</b> a concurrent transaction changed the same account.</p>
     * <p><b>Post-condition:</b> the caller receives 409; the failed transaction is rolled back.</p>
     * @param ex the optimistic locking failure
     * @return a consistent conflict response
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDTO> handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDTO(409, "Conflict",
                "The account changed during this request. Refresh balances before retrying."));
    }

    /**
     * Catch-all for anything not handled above.
     *
     * <p><b>Pre-condition:</b> an exception reached the web layer unhandled.</p>
     * <p><b>Post-condition:</b> a response is built that never leaks a stack trace.</p>
     *
     * @param ex the exception
     * @return the exception's own status for Spring web errors, 401 for authentication
     *         problems, otherwise 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleEverythingElse(Exception ex) {
        // Exceptions that already carry an HTTP status (e.g. 409 from registration) keep that status
        if (ex instanceof ResponseStatusException response) {
            int status = response.getStatusCode().value();
            HttpStatus known = HttpStatus.resolve(status);
            return ResponseEntity.status(status).body(new ErrorResponseDTO(status,
                    known == null ? "Request Error" : known.getReasonPhrase(), response.getReason()));
        }
        // Spring MVC errors such as 404 no-handler or 405 method-not-allowed
        if (ex instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            return ResponseEntity.status(status)
                    .body(new ErrorResponseDTO(status, "Request Error", ex.getMessage()));
        }
        // Authentication problems raised inside a controller are still a 401
        if (ex instanceof AuthenticationException) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponseDTO(401, "Unauthorized",
                            "Access denied: No token or invalid token provided"));
        }
        // Anything else is an unexpected bug: answer 500 without leaking the stack trace or message
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                "An unexpected error occurred. Please contact support.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
