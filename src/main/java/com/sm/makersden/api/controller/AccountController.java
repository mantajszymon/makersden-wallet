package com.sm.makersden.api.controller;

import com.sm.makersden.api.dto.AccountResponse;
import com.sm.makersden.api.dto.CreateAccountRequest;
import com.sm.makersden.core.model.Account;
import com.sm.makersden.core.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final LedgerService ledgerService;

    @PostMapping
    public ResponseEntity<AccountResponse> create(@RequestBody CreateAccountRequest request) {
        Account account = ledgerService.createAccount(request.openingBalance());
        return ResponseEntity.created(URI.create("/accounts/" + account.getId()))
                .body(new AccountResponse(account.getId(), account.getBalance()));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable String id) {
        BigDecimal balance = ledgerService.getBalance(id);
        return new AccountResponse(id, balance);
    }
}
