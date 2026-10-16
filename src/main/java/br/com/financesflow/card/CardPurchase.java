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
@Table(name = "card_purchases")
public class CardPurchase {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private CreditCard card;

    @Column(nullable = false, length = 120)
    private String description;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "invoice_month", nullable = false)
    private LocalDate invoiceMonth;

    @Column(name = "installment_count", nullable = false)
    private int installmentCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CardPurchase() {
    }

    public CardPurchase(CreditCard card, String description, String category, BigDecimal amount,
                        LocalDate purchaseDate, LocalDate invoiceMonth, int installmentCount) {
        this.card = card;
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.purchaseDate = purchaseDate;
        this.invoiceMonth = invoiceMonth;
        this.installmentCount = installmentCount;
    }

    @PrePersist
    void prepareForInsert() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public CreditCard getCard() { return card; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public LocalDate getInvoiceMonth() { return invoiceMonth; }
    public int getInstallmentCount() { return installmentCount; }
}
