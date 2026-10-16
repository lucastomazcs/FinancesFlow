package br.com.financesflow.card;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditCardResponse(
        UUID id,
        String name,
        BigDecimal creditLimit,
        BigDecimal availableLimit,
        int closingDay,
        int dueDay,
        Instant createdAt
) {
}
