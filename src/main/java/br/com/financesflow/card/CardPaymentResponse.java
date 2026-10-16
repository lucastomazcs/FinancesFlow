package br.com.financesflow.card;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

public record CardPaymentResponse(UUID cardId, YearMonth month, BigDecimal amount, Instant paidAt) { }
