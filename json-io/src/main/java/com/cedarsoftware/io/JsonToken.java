package com.cedarsoftware.io;

/**
 * Token-type enumeration emitted by {@link JsonTokenizer} as it scans a JSON
 * document. Names mirror Jackson's {@code com.fasterxml.jackson.core.JsonToken}
 * exactly so callers familiar with the Jackson streaming API can switch over by
 * changing imports.
 *
 * <p>Conscious divergences from Jackson:
 * <ul>
 *   <li>No {@code NOT_AVAILABLE} — json-io is sync-only.</li>
 *   <li>No {@code VALUE_EMBEDDED_OBJECT} — pure JSON, no binary embed.</li>
 *   <li>No {@code END_DOCUMENT} — EOF is signaled by {@code nextToken()}
 *       returning {@code null}, matching Jackson's convention.</li>
 * </ul>
 *
 * <p>Number tokens carry only shape information ({@link #VALUE_NUMBER_INT} vs
 * {@link #VALUE_NUMBER_FLOAT}). The precise numeric type
 * ({@code int}, {@code long}, {@code BigInteger}, ...) is queried via
 * {@link JsonTokenizer#getNumberType()}.
 *
 * <p>Package-private until 4.104.0+; stays internal while the surrounding API
 * stabilizes.
 */
enum JsonToken {
    /** {@code '{'} — start of an object. */
    START_OBJECT,
    /** {@code '}'} — end of an object. */
    END_OBJECT,
    /** {@code '['} — start of an array. */
    START_ARRAY,
    /** {@code ']'} — end of an array. */
    END_ARRAY,
    /** Field name half of an object property. */
    FIELD_NAME,
    /** String value (including JSON5 single-quoted strings). */
    VALUE_STRING,
    /** Integer-shaped number — {@code int}, {@code long}, or {@code BigInteger}. */
    VALUE_NUMBER_INT,
    /** Decimal-shaped number — {@code float}, {@code double}, or {@code BigDecimal}. */
    VALUE_NUMBER_FLOAT,
    /** The literal {@code true}. */
    VALUE_TRUE,
    /** The literal {@code false}. */
    VALUE_FALSE,
    /** The literal {@code null}. */
    VALUE_NULL
}
