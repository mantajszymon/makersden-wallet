package com.sm.makersden.core.util;

import com.sm.makersden.core.exception.InsufficientFundsException;
import com.sm.makersden.core.exception.SelfTransferException;
import com.sm.makersden.core.model.Account;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Validation specific to a transfer between two {@link Account}s: that the source and
 * destination differ, and that the source can cover the amount. Kept separate from
 * {@link MoneyValidation} because these checks need the two accounts involved, not
 * just a bare amount.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferValidation {

    /**
     * @throws SelfTransferException if from and to are the same account.
     */
    public static void rejectSelfTransfer(Account from, Account to) throws SelfTransferException {
        if (from.getId().equals(to.getId())) {
            throw new SelfTransferException(from.getId());
        }
    }

    /**
     * @param validAmount an amount already normalized by {@link MoneyValidation#requireValidAmount}.
     * @throws InsufficientFundsException if from's balance is less than validAmount.
     */
    public static void rejectInsufficientFundsTransfer(Account from, BigDecimal validAmount) throws InsufficientFundsException {
        BigDecimal fromBalance = from.getBalance();
        if (fromBalance.compareTo(validAmount) < 0) {
            throw new InsufficientFundsException(from.getId(), validAmount, fromBalance);
        }
    }
}
