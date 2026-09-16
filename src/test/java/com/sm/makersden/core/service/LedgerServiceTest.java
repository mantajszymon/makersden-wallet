package com.sm.makersden.core.service;

import com.sm.makersden.core.exception.AccountNotFoundException;
import com.sm.makersden.core.exception.InsufficientFundsException;
import com.sm.makersden.core.exception.InvalidAmountException;
import com.sm.makersden.core.exception.SelfTransferException;
import com.sm.makersden.core.model.Account;
import com.sm.makersden.core.repository.InMemoryAccountRepository;
import com.sm.makersden.core.repository.InMemoryIdempotencyKeyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceTest {

    private LedgerService ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerService(new InMemoryAccountRepository(), new InMemoryIdempotencyKeyStore());
    }

    @Test
    void createAccountAssignsAGeneratedIdAndTheGivenOpeningBalance() {
        // given
        BigDecimal openingBalance = new BigDecimal("100.00");

        // when
        Account account = ledger.createAccount(openingBalance);

        // then
        assertThat(account.getId()).isNotBlank();
        assertThat(account.getBalance()).isEqualByComparingTo(openingBalance);
    }

    @Test
    void createAccountAcceptsAZeroOpeningBalance() {
        // when
        Account account = ledger.createAccount(BigDecimal.ZERO);

        // then
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void twoAccountsGetDifferentGeneratedIds() {
        // when
        Account firstAccount = ledger.createAccount(BigDecimal.ZERO);
        Account secondAccount = ledger.createAccount(BigDecimal.ZERO);

        // then
        assertThat(firstAccount.getId()).isNotEqualTo(secondAccount.getId());
    }

    @Test
    void generatedAccountIdsAreSequential() {
        // when
        Account firstAccount = ledger.createAccount(BigDecimal.ZERO);
        Account secondAccount = ledger.createAccount(BigDecimal.ZERO);

        // then
        long firstId = Long.parseLong(firstAccount.getId());
        long secondId = Long.parseLong(secondAccount.getId());
        assertThat(secondId).isEqualTo(firstId + 1);
    }

    @Test
    void concurrentAccountCreationNeverProducesDuplicateIds() throws Exception {
        // given
        int concurrentCreations = 200;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        // when
        Set<String> ids;
        try {
            for (int creationIndex = 0; creationIndex < concurrentCreations; creationIndex++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return ledger.createAccount(BigDecimal.ZERO).getId();
                }));
            }
            startGate.countDown();

            ids = new HashSet<>();
            for (Future<String> future : futures) {
                ids.add(future.get());
            }
        } finally {
            executor.shutdown();
        }

        // then
        assertThat(ids).hasSize(concurrentCreations);
    }

    @Test
    void createAccountRejectsANegativeOpeningBalance() {
        // when / then
        assertThatThrownBy(() -> ledger.createAccount(new BigDecimal("-0.01")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void getBalanceReturnsTheCurrentBalanceForAKnownAccount() {
        // given
        Account account = ledger.createAccount(new BigDecimal("42.00"));

        // when
        BigDecimal balance = ledger.getBalance(account.getId());

        // then
        assertThat(balance).isEqualByComparingTo("42.00");
    }

    @Test
    void getBalanceThrowsForAnUnknownAccount() {
        // when / then
        assertThatThrownBy(() -> ledger.getBalance("does-not-exist"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getBalanceRejectsANullAccountId() {
        // when / then
        assertThatThrownBy(() -> ledger.getBalance(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void transferMovesMoneyFromOneAccountToAnother() {
        // given
        Account from = ledger.createAccount(new BigDecimal("100.00"));
        Account to = ledger.createAccount(new BigDecimal("10.00"));

        // when
        ledger.transfer(from.getId(), to.getId(), new BigDecimal("25.00"));

        // then
        assertThat(ledger.getBalance(from.getId())).isEqualByComparingTo("75.00");
        assertThat(ledger.getBalance(to.getId())).isEqualByComparingTo("35.00");
    }

    @Test
    void transferRejectsInsufficientFundsAndLeavesBothBalancesUnchanged() {
        // given
        Account from = ledger.createAccount(new BigDecimal("10.00"));
        Account to = ledger.createAccount(new BigDecimal("5.00"));

        // when
        assertThatThrownBy(() -> ledger.transfer(from.getId(), to.getId(), new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class);

        // then
        assertThat(ledger.getBalance(from.getId())).isEqualByComparingTo("10.00");
        assertThat(ledger.getBalance(to.getId())).isEqualByComparingTo("5.00");
    }

    @Test
    void transferRejectsATransferFromAnAccountToItself() {
        // given
        Account account = ledger.createAccount(new BigDecimal("10.00"));

        // when / then
        assertThatThrownBy(() -> ledger.transfer(account.getId(), account.getId(), new BigDecimal("1.00")))
                .isInstanceOf(SelfTransferException.class);
    }

    @Test
    void transferThrowsWhenTheSourceAccountDoesNotExist() {
        // given
        Account to = ledger.createAccount(BigDecimal.ZERO);

        // when / then
        assertThatThrownBy(() -> ledger.transfer("does-not-exist", to.getId(), new BigDecimal("1.00")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void transferThrowsWhenTheDestinationAccountDoesNotExist() {
        // given
        Account from = ledger.createAccount(new BigDecimal("10.00"));

        // when / then
        assertThatThrownBy(() -> ledger.transfer(from.getId(), "does-not-exist", new BigDecimal("1.00")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void transferRejectsANonPositiveAmount() {
        // given
        Account from = ledger.createAccount(new BigDecimal("10.00"));
        Account to = ledger.createAccount(BigDecimal.ZERO);

        // when / then
        assertThatThrownBy(() -> ledger.transfer(from.getId(), to.getId(), BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class);
    }
}
