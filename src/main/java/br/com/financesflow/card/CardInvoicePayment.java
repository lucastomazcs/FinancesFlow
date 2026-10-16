package br.com.financesflow.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "card_invoice_payments")
public class CardInvoicePayment {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private CreditCard card;

    @Column(name = "invoice_month", nullable = false)
    private LocalDate invoiceMonth;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    protected CardInvoicePayment() { }

    public CardInvoicePayment(CreditCard card, LocalDate invoiceMonth, BigDecimal amount) {
        this.card = card;
        this.invoiceMonth = invoiceMonth;
        this.amount = amount;
    }

    @PrePersist
    void prepareForInsert() {
        if (id == null) id = UUID.randomUUID();
        if (paidAt == null) paidAt = Instant.now();
    }

    public UUID getId() { return id; }
    public CreditCard getCard() { return card; }
    public LocalDate getInvoiceMonth() { return invoiceMonth; }
    public BigDecimal getAmount() { return amount; }
    public Instant getPaidAt() { return paidAt; }
}
