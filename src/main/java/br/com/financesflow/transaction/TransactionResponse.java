package br.com.financesflow.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String description,
        BigDecimal amount,
        TransactionType type,
        String category,
        LocalDate transactionDate,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(), transaction.getDescription(), transaction.getAmount(),
                transaction.getType(), transaction.getCategory(), transaction.getTransactionDate(),
                transaction.getCreatedAt());
    }
}
