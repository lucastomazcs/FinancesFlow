package br.com.financesflow.card;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.financesflow.user.AppUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InstallmentMathTest {

    @Test
    void divideAmountAndPutRoundingDifferenceInLastInstallment() {
        // Arrange: preparamos uma compra de R$ 100 em 3 parcelas.
        var owner = new AppUser("Lucas", "lucas@example.com", "hash");
        var card = new CreditCard(owner, "Cartão de teste",
                new BigDecimal("1000.00"), 10, 17);
        var purchase = new CardPurchase(card, "Compra teste", "Teste",
                new BigDecimal("100.00"), LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 1), 3);

        // Act: calculamos o valor de cada parcela.
        var first = InstallmentMath.amountFor(purchase, 1);
        var second = InstallmentMath.amountFor(purchase, 2);
        var third = InstallmentMath.amountFor(purchase, 3);

        // Assert: conferimos os valores esperados.
        assertEquals(new BigDecimal("33.33"), first);
        assertEquals(new BigDecimal("33.33"), second);
        assertEquals(new BigDecimal("33.34"), third);
    }
}
