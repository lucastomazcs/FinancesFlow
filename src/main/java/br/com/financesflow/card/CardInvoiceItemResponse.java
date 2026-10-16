package br.com.financesflow.card;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CardInvoiceItemResponse(
        UUID purchaseId,
        String description,
        String category,
        BigDecimal amount,
        int installmentNumber,
        int installmentCount,
        LocalDate purchaseDate
) {
}
