package br.com.financesflow.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import br.com.financesflow.user.AppUserRepository;
import java.time.YearMonth;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CreditCardServiceTest {

    private CreditCardRepository cardRepository;
    private CardPurchaseRepository purchaseRepository;
    private AppUserRepository userRepository;
    private CardInvoicePaymentRepository paymentRepository;
    private CreditCardService service;

    @BeforeEach
    void setUp() {
        cardRepository = mock(CreditCardRepository.class);
        purchaseRepository = mock(CardPurchaseRepository.class);
        userRepository = mock(AppUserRepository.class);
        paymentRepository = mock(CardInvoicePaymentRepository.class);
        service = new CreditCardService(cardRepository, purchaseRepository,
                userRepository, paymentRepository);
    }

    @Test
    void payingAnAlreadyPaidInvoiceReturnsConflict() {
        // Arrange: o cartão pertence ao usuário e já existe pagamento para novembro.
        var ownerId = UUID.randomUUID();
        var cardId = UUID.randomUUID();
        var month = YearMonth.of(2026, 11);
        var card = mock(CreditCard.class);
        var existingPayment = mock(CardInvoicePayment.class);

        when(cardRepository.findByIdAndOwner_Id(cardId, ownerId))
                .thenReturn(Optional.of(card));
        when(paymentRepository.findByCard_IdAndInvoiceMonth(cardId, month.atDay(1)))
                .thenReturn(Optional.of(existingPayment));

        // Act: tentamos pagar a mesma fatura novamente.
        var exception = assertThrows(ResponseStatusException.class,
                () -> service.payInvoice(ownerId, cardId, month));

        // Assert: o serviço retorna 409 e nem recalcula a fatura.
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(purchaseRepository, never())
                .findAllByCard_IdAndInvoiceMonthLessThanEqualOrderByPurchaseDateAsc(
                        cardId, month.atDay(1));
    }

    @Test
    void payingAnOpenInvoiceRecordsItsTotal() {
        // Arrange: a fatura de novembro contém uma compra de R$ 80,00.
        var ownerId = UUID.randomUUID();
        var cardId = UUID.randomUUID();
        var month = YearMonth.of(2026, 11);
        var card = mock(CreditCard.class);
        when(card.getId()).thenReturn(cardId);
        when(card.getName()).thenReturn("Cartão de teste");
        when(card.getDueDay()).thenReturn(17);
        when(cardRepository.findByIdAndOwner_Id(cardId, ownerId))
                .thenReturn(Optional.of(card));
        when(paymentRepository.findByCard_IdAndInvoiceMonth(cardId, month.atDay(1)))
                .thenReturn(Optional.empty());

        var purchase = new CardPurchase(card, "Compra teste", "Teste",
                new BigDecimal("80.00"), LocalDate.of(2026, 10, 10),
                month.atDay(1), 1);
        when(purchaseRepository.findAllByCard_IdAndInvoiceMonthLessThanEqualOrderByPurchaseDateAsc(
                cardId, month.atDay(1))).thenReturn(List.of(purchase));
        when(paymentRepository.save(any(CardInvoicePayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: pagamos a fatura.
        var response = service.payInvoice(ownerId, cardId, month);

        // Assert: o total correto foi devolvido e registrado no repositório.
        assertEquals(new BigDecimal("80.00"), response.amount());
        assertEquals(month, response.month());
        var savedPayment = org.mockito.ArgumentCaptor.forClass(CardInvoicePayment.class);
        verify(paymentRepository).save(savedPayment.capture());
        assertEquals(new BigDecimal("80.00"), savedPayment.getValue().getAmount());
        assertEquals(month.atDay(1), savedPayment.getValue().getInvoiceMonth());
    }

    @Test
    void paidInstallmentIsReleasedButFutureInstallmentsStillUseCreditLimit() {
        // Arrange: cartão de R$ 2.500, compra de R$ 300 em 3 parcelas,
        // e a primeira parcela (novembro) já foi paga.
        var ownerId = UUID.randomUUID();
        var cardId = UUID.randomUUID();
        var firstInvoiceMonth = YearMonth.of(2026, 11);
        var card = mock(CreditCard.class);
        when(card.getId()).thenReturn(cardId);
        when(card.getName()).thenReturn("Cartão de teste");
        when(card.getCreditLimit()).thenReturn(new BigDecimal("2500.00"));
        when(card.getClosingDay()).thenReturn(10);
        when(card.getDueDay()).thenReturn(17);
        when(cardRepository.findAllByOwner_IdOrderByNameAsc(ownerId))
                .thenReturn(List.of(card));

        var purchase = new CardPurchase(card, "Compra parcelada", "Teste",
                new BigDecimal("300.00"), LocalDate.of(2026, 10, 10),
                firstInvoiceMonth.atDay(1), 3);
        when(purchaseRepository.findAllByCard_Id(cardId)).thenReturn(List.of(purchase));

        var paidNovember = mock(CardInvoicePayment.class);
        when(paidNovember.getInvoiceMonth()).thenReturn(firstInvoiceMonth.atDay(1));
        when(paymentRepository.findAllByCard_Id(cardId)).thenReturn(List.of(paidNovember));

        // Act: consultamos os cartões, que incluem o limite disponível calculado.
        var cards = service.findAll(ownerId);

        // Assert: novembro foi liberado; dezembro e janeiro (R$ 200) ainda contam.
        assertEquals(new BigDecimal("2300.00"), cards.get(0).availableLimit());
    }
}
