package com.sm.makersden.api.controller;

import com.sm.makersden.api.dto.TransferRequest;
import com.sm.makersden.core.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final LedgerService ledgerService;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@RequestBody TransferRequest request,
                          @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireNonBlank(request.fromAccountId(), "fromAccountId");
        requireNonBlank(request.toAccountId(), "toAccountId");
        requireNonBlank(idempotencyKey, "Idempotency-Key");

        ledgerService.transfer(request.fromAccountId(), request.toAccountId(), request.amount(), idempotencyKey);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must not be blank");
        }
    }
}
