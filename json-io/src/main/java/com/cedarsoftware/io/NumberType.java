package com.cedarsoftware.io;

/**
 * Precise numeric type associated with a {@link JsonToken#VALUE_NUMBER_INT} or
 * {@link JsonToken#VALUE_NUMBER_FLOAT} token. Mirrors Jackson's
 * {@code com.fasterxml.jackson.core.JsonParser.NumberType}.
 *
 * <p>Jackson nests the enum inside its {@code JsonParser} class. json-io has no
 * single public {@code JsonParser} class to nest under, so this is a top-level
 * (package-private) type instead.
 *
 * <p>Returned values:
 * <ul>
 *   <li>{@link #INT}, {@link #LONG}, {@link #BIG_INTEGER} — for
 *       {@link JsonToken#VALUE_NUMBER_INT} tokens.</li>
 *   <li>{@link #FLOAT}, {@link #DOUBLE}, {@link #BIG_DECIMAL} — for
 *       {@link JsonToken#VALUE_NUMBER_FLOAT} tokens.</li>
 * </ul>
 *
 * <p>Package-private until 4.104.0+.
 */
enum NumberType {
    /** Fits in a Java {@code int}. */
    INT,
    /** Fits in a Java {@code long} but not an {@code int}. */
    LONG,
    /** Integer too large for {@code long}; materialized as {@code BigInteger}. */
    BIG_INTEGER,
    /** Decimal value materialized as a Java {@code float}. */
    FLOAT,
    /** Decimal value materialized as a Java {@code double}. */
    DOUBLE,
    /** Decimal materialized as {@code BigDecimal} (high precision). */
    BIG_DECIMAL
}
