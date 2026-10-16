package br.com.financesflow.card;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CardPurchaseResponse(
        UUID id,
        String description,
        String category,
        BigDecimal totalAmount,
        int installments,
        LocalDate purchaseDate,
        LocalDate firstInvoiceMonth
) {
}
