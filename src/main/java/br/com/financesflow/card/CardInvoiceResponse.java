package br.com.financesflow.card;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CardInvoiceResponse(
        UUID cardId,
        String cardName,
        YearMonth month,
        LocalDate dueDate,
        BigDecimal total,
        boolean paid,
        Instant paidAt,
        List<CardInvoiceItemResponse> purchases
) {
}
