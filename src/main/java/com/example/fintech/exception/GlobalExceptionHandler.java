package com.example.fintech.exception;

import com.example.fintech.account.exception.AccountNotFoundException;
import com.example.fintech.auth.exception.InvalidCredentialsException;
import com.example.fintech.payment.exception.CurrencyMismatchException;
import com.example.fintech.payment.exception.InsufficientBalanceException;
import com.example.fintech.user.exception.UserAlreadyExistsException;
import com.example.fintech.user.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyMismatch(
            CurrencyMismatchException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(
            InsufficientBalanceException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        exception.getMessage()
                ));
    }
}