package com.sm.makersden.core.util;

import com.sm.makersden.core.exception.InsufficientFundsException;
import com.sm.makersden.core.exception.SelfTransferException;
import com.sm.makersden.core.model.Account;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferValidationTest {

    @Test
    void rejectSelfTransferThrowsWhenFromAndToAreTheSameAccount() {
        // given
        Account account = new Account("acc-1", new BigDecimal("10.00"));

        // when / then
        assertThatThrownBy(() -> TransferValidation.rejectSelfTransfer(account, account))
                .isInstanceOf(SelfTransferException.class);
    }

    @Test
    void rejectSelfTransferAllowsTwoDifferentAccounts() {
        // given
        Account from = new Account("acc-1", new BigDecimal("10.00"));
        Account to = new Account("acc-2", new BigDecimal("10.00"));

        // when / then
        assertThatCode(() -> TransferValidation.rejectSelfTransfer(from, to)).doesNotThrowAnyException();
    }

    @Test
    void rejectInsufficientFundsTransferThrowsWhenBalanceIsBelowTheAmount() {
        // given
        Account from = new Account("acc-1", new BigDecimal("10.00"));
        BigDecimal amount = new BigDecimal("10.01");

        // when / then
        assertThatThrownBy(() -> TransferValidation.rejectInsufficientFundsTransfer(from, amount))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void rejectInsufficientFundsTransferAllowsDrainingTheBalanceExactly() {
        // given
        Account from = new Account("acc-1", new BigDecimal("10.00"));
        BigDecimal amount = new BigDecimal("10.00");

        // when / then
        assertThatCode(() -> TransferValidation.rejectInsufficientFundsTransfer(from, amount))
                .doesNotThrowAnyException();
    }
}
