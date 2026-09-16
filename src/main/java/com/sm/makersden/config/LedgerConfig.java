package com.sm.makersden.config;

import com.sm.makersden.core.repository.AccountRepository;
import com.sm.makersden.core.repository.IdempotencyKeyStore;
import com.sm.makersden.core.repository.InMemoryAccountRepository;
import com.sm.makersden.core.repository.InMemoryIdempotencyKeyStore;
import com.sm.makersden.core.service.LedgerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerConfig {

    @Bean
    public AccountRepository accountRepository() {
        return new InMemoryAccountRepository();
    }

    @Bean
    public IdempotencyKeyStore idempotencyKeyStore() {
        return new InMemoryIdempotencyKeyStore();
    }

    @Bean
    public LedgerService ledgerService(AccountRepository accountRepository, IdempotencyKeyStore idempotencyKeyStore) {
        return new LedgerService(accountRepository, idempotencyKeyStore);
    }
}
