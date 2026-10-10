package com.aifsar.pos.payment.web;

import com.aifsar.pos.payment.domain.InvalidTransitionException;
import com.aifsar.pos.payment.service.PaymentExceptions.IdempotencyConflictException;
import com.aifsar.pos.payment.service.PaymentExceptions.IssuerRejectedException;
import com.aifsar.pos.payment.service.PaymentExceptions.IssuerRetryableException;
import com.aifsar.pos.payment.service.PaymentExceptions.PaymentNotFoundException;
import com.aifsar.pos.payment.service.PaymentExceptions.RequestInProgressException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(RequestInProgressException.class)
    ResponseEntity<ProblemDetail> inProgress(RequestInProgressException e) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        body.setProperty("paymentId", e.getPaymentId());
        return ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.RETRY_AFTER, "1").body(body);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidTransitionException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IssuerRetryableException.class)
    ResponseEntity<ProblemDetail> retryable(IssuerRetryableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()));
    }

    @ExceptionHandler(IssuerRejectedException.class)
    ProblemDetail rejected(IssuerRejectedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
