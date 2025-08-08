package ca.homefinance.helper;

import ca.homefinance.entity.Transaction;

import java.math.BigDecimal;

public class GeneralHelper {
    public static Transaction.TransactionType determineTransactionType(String entity, BigDecimal amount) {
        Transaction.TransactionType type;
        if (amount.signum() < 0) {
            // Negative amount → refund or payment?
            if (entity.toLowerCase().contains("payment")) {
                type = Transaction.TransactionType.CARDPAYMENT;
            } else {
                type = Transaction.TransactionType.REFUND;
            }
        } else {
            type = Transaction.TransactionType.EXPENSE;
        }
        return type;
    }
}
