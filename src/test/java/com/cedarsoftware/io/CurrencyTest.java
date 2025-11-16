package com.cedarsoftware.io;

import java.util.Currency;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CurrencyTest {

    @Test
    void testCurrencyStandalone() {
        Currency expected = Currency.getInstance("USD");
        String json = TestUtil.toJson(expected);
        Currency actual = TestUtil.toJava(json, null).asClass(null);
        assertEquals(expected, actual);
    }

    @Test
    void testCurrencyArray() {
        Currency currency = Currency.getInstance("EUR");
        Currency[] array = new Currency[]{ currency };
        String json = TestUtil.toJson(array);
        Currency[] actual = TestUtil.toJava(json, null).asClass(null);
        assertEquals(1, actual.length);
        assertEquals(currency, actual[0]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCurrencyAsMapValue() {
        Currency currency = Currency.getInstance("GBP");
        Map<String, Currency> map = mapOf("c", currency);
        String json = TestUtil.toJson(map);
        Map<String, Currency> actual = TestUtil.toJava(json, null).asClass(null);
        assertThat(actual).hasSize(1);
        assertEquals(currency, actual.get("c"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCurrencyAsMapKey() {
        Currency currency = Currency.getInstance("JPY");
        Map<Currency, String> map = mapOf(currency, "yen");
        String json = TestUtil.toJson(map);
        Map<Currency, String> actual = TestUtil.toJava(json, null).asClass(null);
        assertThat(actual).hasSize(1);
        Currency key = actual.keySet().iterator().next();
        assertEquals(currency, key);
    }

    @Test
    void testCurrencyReference() {
        Currency currency = Currency.getInstance("CHF");
        Object[] array = new Object[]{ currency, currency };
        String json = TestUtil.toJson(array);
        Object[] actual = TestUtil.toJava(json, null).asClass(null);
        assertSame(actual[0], actual[1]);
        assertEquals(currency, actual[0]);
    }
}
