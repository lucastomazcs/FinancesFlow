package br.com.financesflow.card;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardInvoicePaymentRepository extends JpaRepository<CardInvoicePayment, UUID> {
    Optional<CardInvoicePayment> findByCard_IdAndInvoiceMonth(UUID cardId, LocalDate invoiceMonth);
    List<CardInvoicePayment> findAllByCard_Id(UUID cardId);
}
