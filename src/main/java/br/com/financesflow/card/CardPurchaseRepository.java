package br.com.financesflow.card;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardPurchaseRepository extends JpaRepository<CardPurchase, UUID> {
    List<CardPurchase> findAllByCard_IdAndInvoiceMonthLessThanEqualOrderByPurchaseDateAsc(
            UUID cardId, LocalDate invoiceMonth);

    List<CardPurchase> findAllByCard_Owner_IdAndInvoiceMonthLessThanEqual(
            UUID ownerId, LocalDate invoiceMonth);

    List<CardPurchase> findAllByCard_Owner_IdAndCategoryIgnoreCaseAndInvoiceMonthLessThanEqual(
            UUID ownerId, String category, LocalDate invoiceMonth);

    List<CardPurchase> findAllByCard_Id(UUID cardId);
}
