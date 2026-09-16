package com.sm.makersden.core.util;

import com.sm.makersden.core.exception.InvalidAmountException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared validation for {@link BigDecimal} amounts (account balances, transfer
 * amounts): scale, non-negativity, and strict positivity.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MoneyValidation {

    private static final int SCALE = 2;

    /**
     * Validates a transfer amount: normalizes it to scale {@value #SCALE} and requires
     * it to be strictly positive.
     *
     * @return amount normalized to scale {@value #SCALE}.
     * @throws InvalidAmountException if amount is null, has more than {@value #SCALE}
     *                                 decimal places, or is not strictly positive.
     */
    public static BigDecimal requireValidAmount(BigDecimal amount) {
        BigDecimal validAmount = requireValidScale(amount, "amount");
        requirePositive(validAmount, "amount");
        return validAmount;
    }

    /**
     * Validates an account balance: normalizes it to scale {@value #SCALE} and requires
     * it to be non-negative.
     *
     * @return balance normalized to scale {@value #SCALE}.
     * @throws InvalidAmountException if balance is null, has more than {@value #SCALE}
     *                                 decimal places, or is negative.
     */
    public static BigDecimal requireValidBalance(BigDecimal balance) {
        BigDecimal validBalance = MoneyValidation.requireValidScale(balance, "balance");
        MoneyValidation.requireNonNegative(validBalance, "balance");
        return validBalance;
    }

    /**
     * @return amount normalized to scale {@value #SCALE}.
     * @throws InvalidAmountException if amount is null or has more than
     *                                 {@value #SCALE} decimal places.
     */
    static BigDecimal requireValidScale(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new InvalidAmountException(String.format("%s must not be null", fieldName));
        }
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidAmountException(
                    String.format("%s %s has more than %d decimal places", fieldName, amount, SCALE));
        }
    }

    /**
     * @throws InvalidAmountException if amount is negative.
     */
    static void requireNonNegative(@NonNull BigDecimal amount, String fieldName) {
        requireMinimumSignum(amount, fieldName, 0, "must not be negative");
    }

    /**
     * @throws InvalidAmountException if amount is not strictly positive.
     */
    static void requirePositive(@NonNull BigDecimal amount, String fieldName) {
        requireMinimumSignum(amount, fieldName, 1, "must be positive");
    }

    /** Fails if {@code amount.signum() < minSignum}. */
    private static void requireMinimumSignum(BigDecimal amount, String fieldName, int minSignum, String requirement) {
        if (amount.signum() < minSignum) {
            throw new InvalidAmountException(String.format("%s %s: %s", fieldName, requirement, amount));
        }
    }
}
