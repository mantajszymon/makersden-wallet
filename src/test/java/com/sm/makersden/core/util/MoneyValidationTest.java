package com.sm.makersden.core.util;

import com.sm.makersden.core.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyValidationTest {

    @Test
    void requireValidScaleNormalizesAOneDecimalPlaceValueToScaleTwo() {
        // given
        BigDecimal oneDecimalPlaceAmount = new BigDecimal("10.5");

        // when
        BigDecimal result = MoneyValidation.requireValidScale(oneDecimalPlaceAmount, "amount");

        // then
        assertThat(result).isEqualByComparingTo("10.50");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void requireValidScalePadsAWholeNumberWithNoDecimalPlacesToScaleTwo() {
        // given
        BigDecimal wholeNumberAmount = new BigDecimal("100");

        // when
        BigDecimal result = MoneyValidation.requireValidScale(wholeNumberAmount, "amount");

        // then
        assertThat(result).isEqualByComparingTo("100.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void requireValidScaleRejectsMoreThanTwoDecimalPlacesRatherThanSilentlyRounding() {
        // given
        BigDecimal tooManyDecimalPlaces = new BigDecimal("10.505");

        // when / then
        assertThatThrownBy(() -> MoneyValidation.requireValidScale(tooManyDecimalPlaces, "amount"))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void requireValidScaleRejectsNull() {
        // when / then
        assertThatThrownBy(() -> MoneyValidation.requireValidScale(null, "amount"))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void requireNonNegativeRejectsANegativeAmount() {
        // given
        BigDecimal negativeAmount = new BigDecimal("-0.01");

        // when / then
        assertThatThrownBy(() -> MoneyValidation.requireNonNegative(negativeAmount, "balance"))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void requireNonNegativeAcceptsZero() {
        // when / then
        MoneyValidation.requireNonNegative(BigDecimal.ZERO, "balance");
    }

    @Test
    void requirePositiveRejectsZero() {
        // when / then
        assertThatThrownBy(() -> MoneyValidation.requirePositive(BigDecimal.ZERO, "amount"))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void requirePositiveAcceptsAPositiveAmount() {
        // given
        BigDecimal positiveAmount = new BigDecimal("0.01");

        // when / then
        MoneyValidation.requirePositive(positiveAmount, "amount");
    }

    @Test
    void requireValidAmountNormalizesAndAcceptsAPositiveAmount() {
        // given
        BigDecimal oneDecimalPlaceAmount = new BigDecimal("10.5");

        // when
        BigDecimal result = MoneyValidation.requireValidAmount(oneDecimalPlaceAmount);

        // then
        assertThat(result).isEqualByComparingTo("10.50");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void requireValidAmountRejectsZero() {
        // when / then
        assertThatThrownBy(() -> MoneyValidation.requireValidAmount(BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void requireValidAmountRejectsANegativeAmount() {
        // when / then
        assertThatThrownBy(() -> MoneyValidation.requireValidAmount(new BigDecimal("-1.00")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void requireValidAmountRejectsMoreThanTwoDecimalPlaces() {
        // when / then
        assertThatThrownBy(() -> MoneyValidation.requireValidAmount(new BigDecimal("1.005")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void requireValidAmountRejectsNull() {
        // when / then
        assertThatThrownBy(() -> MoneyValidation.requireValidAmount(null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void requireValidBalanceNormalizesAndAcceptsAZeroBalance() {
        // when
        BigDecimal result = MoneyValidation.requireValidBalance(BigDecimal.ZERO);

        // then
        assertThat(result).isEqualByComparingTo("0.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void requireValidBalanceNormalizesAWholeNumberBalance() {
        // when
        BigDecimal result = MoneyValidation.requireValidBalance(new BigDecimal("100"));

        // then
        assertThat(result).isEqualByComparingTo("100.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void requireValidBalanceRejectsANegativeBalance() {
        // when / then
        assertThatThrownBy(() -> MoneyValidation.requireValidBalance(new BigDecimal("-0.01")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void requireValidBalanceRejectsMoreThanTwoDecimalPlaces() {
        // when / then
        assertThatThrownBy(() -> MoneyValidation.requireValidBalance(new BigDecimal("10.005")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void requireValidBalanceRejectsNull() {
        // when / then
        assertThatThrownBy(() -> MoneyValidation.requireValidBalance(null))
                .isInstanceOf(InvalidAmountException.class);
    }
}
