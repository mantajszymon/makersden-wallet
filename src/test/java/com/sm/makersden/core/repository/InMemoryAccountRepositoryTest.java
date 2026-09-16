package com.sm.makersden.core.repository;

import com.sm.makersden.core.exception.DuplicateAccountException;
import com.sm.makersden.core.model.Account;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAccountRepositoryTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();

    @Test
    void createThenFindByIdReturnsTheSameAccount() {
        // given
        Account account = new Account("acc-1", new BigDecimal("10.00"));

        // when
        repository.create(account);

        // then
        assertThat(repository.findById("acc-1")).contains(account);
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        // when
        Optional<Account> found = repository.findById("nope");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void createRejectsADuplicateId() {
        // given
        repository.create(new Account("acc-1", new BigDecimal("10.00")));

        // when / then
        assertThatThrownBy(() -> repository.create(new Account("acc-1", new BigDecimal("20.00"))))
                .isInstanceOf(DuplicateAccountException.class);
    }
}
