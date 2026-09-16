package com.sm.makersden.core.model;

import com.sm.makersden.core.exception.InsufficientFundsException;
import com.sm.makersden.core.exception.InvalidAmountException;
import com.sm.makersden.core.exception.SelfTransferException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void exposesTheIdAndBalanceItWasConstructedWith() {
        // given
        String accountId = "acc-1";
        BigDecimal openingBalance = new BigDecimal("10.00");

        // when
        Account account = new Account(accountId, openingBalance);

        // then
        assertThat(account.getId()).isEqualTo(accountId);
        assertThat(account.getBalance()).isEqualByComparingTo(openingBalance);
    }

    @ParameterizedTest(name = "rejects opening balance \"{0}\"")
    @ValueSource(strings = {"-0.01", "10.005"})
    void rejectsAnInvalidOpeningBalance(String invalidBalance) {
        // when / then
        assertThatThrownBy(() -> new Account("acc-1", new BigDecimal(invalidBalance)))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void rejectsANullId() {
        // when / then
        assertThatThrownBy(() -> new Account(null, BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsANullBalance() {
        // when / then
        assertThatThrownBy(() -> new Account("acc-1", null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void transferMovesTheAmountFromOneAccountToTheOther() {
        // given
        Account from = new Account("acc-1", new BigDecimal("100.00"));
        Account to = new Account("acc-2", new BigDecimal("10.00"));

        // when
        Account.transfer(from, to, new BigDecimal("30.00"));

        // then
        assertThat(from.getBalance()).isEqualByComparingTo("70.00");
        assertThat(to.getBalance()).isEqualByComparingTo("40.00");
    }

    @Test
    void transferAllowsDrainingAnAccountToExactlyZero() {
        // given
        Account from = new Account("acc-1", new BigDecimal("50.00"));
        Account to = new Account("acc-2", BigDecimal.ZERO);

        // when
        Account.transfer(from, to, new BigDecimal("50.00"));

        // then
        assertThat(from.getBalance()).isEqualByComparingTo("0.00");
        assertThat(to.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferRejectsInsufficientFundsAndLeavesBothBalancesUnchanged() {
        // given
        Account from = new Account("acc-1", new BigDecimal("10.00"));
        Account to = new Account("acc-2", new BigDecimal("5.00"));

        // when
        assertThatThrownBy(() -> Account.transfer(from, to, new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class);

        // then
        assertThat(from.getBalance()).isEqualByComparingTo("10.00");
        assertThat(to.getBalance()).isEqualByComparingTo("5.00");
    }

    @Test
    void transferRejectsATransferFromAnAccountToItself() {
        // given
        Account account = new Account("acc-1", new BigDecimal("10.00"));

        // when
        assertThatThrownBy(() -> Account.transfer(account, account, new BigDecimal("1.00")))
                .isInstanceOf(SelfTransferException.class);

        // then
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }

    @ParameterizedTest(name = "rejects transfer amount \"{0}\"")
    @ValueSource(strings = {"0.00", "-1.00", "1.005"})
    void transferRejectsAnInvalidAmount(String invalidAmount) {
        // given
        Account from = new Account("acc-1", new BigDecimal("10.00"));
        Account to = new Account("acc-2", BigDecimal.ZERO);

        // when / then
        assertThatThrownBy(() -> Account.transfer(from, to, new BigDecimal(invalidAmount)))
                .isInstanceOf(InvalidAmountException.class);
    }
}
