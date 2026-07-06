package com.nexusbank.infrastructure.exception;

import com.nexusbank.corebanking.domain.exception.*;
import com.nexusbank.identity.domain.exception.*;
import com.nexusbank.notifications.domain.exception.NotificationNotFoundException;
import com.nexusbank.payments.domain.exception.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail handleEmailConflict(EmailAlreadyRegisteredException ex, WebRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("/errors/email-already-registered"));
        pd.setTitle("E-mail já cadastrado");
        return pd;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create("/errors/invalid-credentials"));
        pd.setTitle("Credenciais inválidas");
        return pd;
    }

    @ExceptionHandler({InvalidEmailException.class, InvalidCpfException.class})
    ProblemDetail handleValidationDomain(RuntimeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("/errors/validation"));
        pd.setTitle("Dado inválido");
        return pd;
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleNotFound(UserNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("/errors/not-found"));
        pd.setTitle("Não encontrado");
        return pd;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("/errors/not-found"));
        pd.setTitle("Conta não encontrada");
        return pd;
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    ProblemDetail handleAccountConflict(AccountAlreadyExistsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("/errors/account-already-exists"));
        pd.setTitle("Conta já existe");
        return pd;
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("/errors/insufficient-funds"));
        pd.setTitle("Saldo insuficiente");
        return pd;
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    ProblemDetail handleAccountAccessDenied(AccountAccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setType(URI.create("/errors/access-denied"));
        pd.setTitle("Acesso negado");
        return pd;
    }

    @ExceptionHandler(AccountConcurrentModificationException.class)
    ProblemDetail handleConcurrentModification(AccountConcurrentModificationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("/errors/concurrent-modification"));
        pd.setTitle("Conflito de concorrência");
        return pd;
    }

    @ExceptionHandler(DuplicateTransferException.class)
    ProblemDetail handleDuplicateTransfer(DuplicateTransferException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("/errors/duplicate-transfer"));
        pd.setTitle("Transferência duplicada");
        return pd;
    }

    @ExceptionHandler(TransferNotFoundException.class)
    ProblemDetail handleTransferNotFound(TransferNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("/errors/not-found"));
        pd.setTitle("Transferência não encontrada");
        return pd;
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ProblemDetail handleNotificationNotFound(NotificationNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("/errors/not-found"));
        pd.setTitle("Notificação não encontrada");
        return pd;
    }

    @ExceptionHandler(io.github.resilience4j.ratelimiter.RequestNotPermitted.class)
    ProblemDetail handleRateLimit(io.github.resilience4j.ratelimiter.RequestNotPermitted ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Limite de requisições atingido. Tente novamente em breve.");
        pd.setType(URI.create("/errors/rate-limit-exceeded"));
        pd.setTitle("Taxa de requisições excedida");
        return pd;
    }

    @ExceptionHandler(io.github.resilience4j.bulkhead.BulkheadFullException.class)
    ProblemDetail handleBulkhead(io.github.resilience4j.bulkhead.BulkheadFullException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Serviço temporariamente sobrecarregado.");
        pd.setType(URI.create("/errors/service-unavailable"));
        pd.setTitle("Serviço indisponível");
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, detail);
        pd.setType(URI.create("/errors/validation"));
        pd.setTitle("Erro de validação");
        return ResponseEntity.unprocessableEntity().body(pd);
    }
}
