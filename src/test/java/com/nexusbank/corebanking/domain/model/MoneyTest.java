package com.nexusbank.corebanking.domain.model;

import com.nexusbank.corebanking.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money")
class MoneyTest {

    @Test
    @DisplayName("of() com valor de uma casa decimal normaliza para escala 2")
    void of_withValidAmount_createsMoneyWithScale2() {
        Money money = Money.of(new BigDecimal("10.1"), Currency.BRL);

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("10.10"));
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("zero() retorna Money com valor zero")
    void zero_returnsMoneyWithZeroAmount() {
        Money money = Money.zero(Currency.BRL);

        assertThat(money.isZero()).isTrue();
        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("add() com mesma moeda retorna soma correta")
    void add_sameCurrency_returnsSum() {
        Money a = Money.of("30.00", Currency.BRL);
        Money b = Money.of("20.50", Currency.BRL);

        Money result = a.add(b);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("50.50"));
        assertThat(result.currency()).isEqualTo(Currency.BRL);
    }

    @Test
    @DisplayName("subtract() com mesma moeda retorna diferença correta")
    void subtract_sameCurrency_returnsDifference() {
        Money a = Money.of("100.00", Currency.BRL);
        Money b = Money.of("40.75", Currency.BRL);

        Money result = a.subtract(b);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("59.25"));
    }

    @Test
    @DisplayName("subtract() resultando em valor negativo retorna Money negativo (Account é quem rejeita)")
    void subtract_resultingInNegative_returnsNegativeMoney() {
        Money saldo = Money.of("10.00", Currency.BRL);
        Money debito = Money.of("50.00", Currency.BRL);

        Money result = saldo.subtract(debito);

        assertThat(result.isNegative()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("-40.00"));
    }

    @Test
    @DisplayName("add() com moedas diferentes lança CurrencyMismatchException")
    void add_differentCurrencies_throwsCurrencyMismatchException() {
        Money brl = Money.of("10.00", Currency.BRL);
        Money usd = Money.of("10.00", Currency.USD);

        assertThatThrownBy(() -> brl.add(usd))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("BRL")
                .hasMessageContaining("USD");
    }

    @Test
    @DisplayName("subtract() com moedas diferentes lança CurrencyMismatchException")
    void subtract_differentCurrencies_throwsCurrencyMismatchException() {
        Money brl = Money.of("10.00", Currency.BRL);
        Money eur = Money.of("10.00", Currency.EUR);

        assertThatThrownBy(() -> brl.subtract(eur))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("isNegative() retorna true para valor negativo")
    void isNegative_withNegativeAmount_returnsTrue() {
        Money money = Money.of("-1.00", Currency.BRL);

        assertThat(money.isNegative()).isTrue();
        assertThat(money.isPositive()).isFalse();
        assertThat(money.isZero()).isFalse();
    }

    @Test
    @DisplayName("isPositive() retorna true para valor positivo")
    void isPositive_withPositiveAmount_returnsTrue() {
        Money money = Money.of("0.01", Currency.BRL);

        assertThat(money.isPositive()).isTrue();
        assertThat(money.isNegative()).isFalse();
        assertThat(money.isZero()).isFalse();
    }

    @Test
    @DisplayName("isZero() retorna true para valor zero")
    void isZero_withZeroAmount_returnsTrue() {
        Money money = Money.of("0.00", Currency.BRL);

        assertThat(money.isZero()).isTrue();
        assertThat(money.isPositive()).isFalse();
        assertThat(money.isNegative()).isFalse();
    }

    @Test
    @DisplayName("equals() trata 10.00 e 10.0 como iguais (compareTo semântico)")
    void equals_sameValueDifferentScale_isEqual() {
        Money a = Money.of(new BigDecimal("10.00"), Currency.BRL);
        Money b = Money.of(new BigDecimal("10.0"), Currency.BRL);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("isGreaterThan() retorna true quando valor é maior")
    void isGreaterThan_withLargerAmount_returnsTrue() {
        Money maior = Money.of("20.00", Currency.BRL);
        Money menor = Money.of("10.00", Currency.BRL);

        assertThat(maior.isGreaterThan(menor)).isTrue();
        assertThat(menor.isGreaterThan(maior)).isFalse();
    }

    @Test
    @DisplayName("isLessThan() retorna true quando valor é menor")
    void isLessThan_withSmallerAmount_returnsTrue() {
        Money menor = Money.of("5.00", Currency.BRL);
        Money maior = Money.of("15.00", Currency.BRL);

        assertThat(menor.isLessThan(maior)).isTrue();
        assertThat(maior.isLessThan(menor)).isFalse();
    }

    @Test
    @DisplayName("of() com amount nulo lança IllegalArgumentException")
    void of_withNullAmount_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null, Currency.BRL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isGreaterThan() com moedas diferentes lança CurrencyMismatchException")
    void isGreaterThan_differentCurrencies_throwsCurrencyMismatchException() {
        Money brl = Money.of("10.00", Currency.BRL);
        Money usd = Money.of("5.00", Currency.USD);

        assertThatThrownBy(() -> brl.isGreaterThan(usd))
                .isInstanceOf(CurrencyMismatchException.class);
    }
}
