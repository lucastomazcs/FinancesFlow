package br.com.financesflow.budget;

import br.com.financesflow.user.AppUser;
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
@Table(name = "budgets")
public class Budget {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(name = "budget_month", nullable = false)
    private LocalDate budgetMonth;

    @Column(name = "monthly_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Budget() {
    }

    public Budget(AppUser owner, String category, LocalDate budgetMonth, BigDecimal monthlyLimit) {
        this.owner = owner;
        this.category = category;
        this.budgetMonth = budgetMonth;
        this.monthlyLimit = monthlyLimit;
    }

    @PrePersist
    void prepareForInsert() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getCategory() { return category; }
    public LocalDate getBudgetMonth() { return budgetMonth; }
    public BigDecimal getMonthlyLimit() { return monthlyLimit; }
    public Instant getCreatedAt() { return createdAt; }
}
