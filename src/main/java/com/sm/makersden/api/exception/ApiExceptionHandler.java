package com.sm.makersden.api.exception;

import com.sm.makersden.core.exception.AccountNotFoundException;
import com.sm.makersden.core.exception.DuplicateAccountException;
import com.sm.makersden.core.exception.InsufficientFundsException;
import com.sm.makersden.core.exception.InvalidAmountException;
import com.sm.makersden.core.exception.SelfTransferException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InsufficientFundsException.class, DuplicateAccountException.class})
    public ProblemDetail handleConflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({SelfTransferException.class, InvalidAmountException.class})
    public ProblemDetail handleBadRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
