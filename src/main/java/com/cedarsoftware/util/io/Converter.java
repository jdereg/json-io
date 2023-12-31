package com.cedarsoftware.util.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Instance conversion utility.  Convert from primitive to other primitives, plus support for Number, Date,
 * TimeStamp, SQL Date, LocalDate, LocalDateTime, ZonedDateTime, Calendar, Big*, Atomic*, Class, UUID,
 * String, ...<br/>
 * <br/>
 * Converter.convert(value, class) if null passed in, null is returned for most types, which allows "tri-state"
 * Boolean, for example, however, for primitive types, it chooses zero for the numeric ones, `false` for boolean,
 * and 0 for char.<br/>
 * <br/>
 * A Map can be converted to almost all data types.  For some, like UUID, it is expected for the Map to have
 * certain keys ("mostSigBits", "leastSigBits").  For the older Java Date/Time related classes, it expects
 * "time" or "nanos", and for all others, a Map as the source, the "value" key will be used to source the value
 * for the conversion.<br/>
 * <br/>
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */

public final class Converter {
    public static final String NOPE = "~nope!";
    public static final String VALUE = "_v";
    private static final String VALUE2 = "value";
    private static final Byte BYTE_ZERO = (byte) 0;
    private static final Byte BYTE_ONE = (byte) 1;
    private static final Short SHORT_ZERO = (short) 0;
    private static final Short SHORT_ONE = (short) 1;
    private static final Integer INTEGER_ZERO = 0;
    private static final Integer INTEGER_ONE = 1;
    private static final Long LONG_ZERO = 0L;
    private static final Long LONG_ONE = 1L;
    private static final Float FLOAT_ZERO = 0.0f;
    private static final Float FLOAT_ONE = 1.0f;
    private static final Double DOUBLE_ZERO = 0.0d;
    private static final Double DOUBLE_ONE = 1.0d;

    private static final Map<Class<?>, Convert<?>> toTypes = new HashMap<>();
    private static final Map<Class<?>, Map<Class<?>, Convert<?>>> targetTypes = new HashMap<>();
    private static final Map<Class<?>, Object> fromNull = new HashMap<>();
    private static final Map<Map.Entry<Class<?>, Class<?>>, Convert<?>> factory = new HashMap<>();
    private static final Map<Map.Entry<Class<?>, Class<?>>, Convert<?>> userDefined = new HashMap<>();
    private static final Map<Class<?>, Set<Class<?>>> cacheParentTypes = new ConcurrentHashMap<>();

    // These are for speed. 'supportedTypes' contains both bounded and unbounded list.
    // These remove 1 map lookup for bounded (known) types.
    private static final Map<Class<?>, Convert<?>> toStr = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toMap = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toAtomicLong = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toDate = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toSqlDate = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toTimestamp = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toCalendar = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toLocalDate = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toLocalDateTime = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toZonedDateTime = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toUUID = new HashMap<>();

    public interface Convert<T> {
        T convert(Object fromInstance);
    }

    private static Map.Entry<Class<?>, Class<?>> pair(Class<?> source, Class<?> target)
    {
        return new AbstractMap.SimpleImmutableEntry<>(source, target);
    }

    static {
        fromNull.put(String.class, null);
        fromNull.put(AtomicLong.class, null);
        fromNull.put(Date.class, null);
        fromNull.put(java.sql.Date.class, null);
        fromNull.put(Timestamp.class, null);
        fromNull.put(Calendar.class, null);
        fromNull.put(GregorianCalendar.class, null);
        fromNull.put(LocalDate.class, null);
        fromNull.put(LocalDateTime.class, null);
        fromNull.put(ZonedDateTime.class, null);
        fromNull.put(UUID.class, null);
        fromNull.put(Map.class, null);

        // Byte/byte Conversions supported
        factory.put(pair(Void.class, byte.class), fromInstance -> (byte)0);
        factory.put(pair(Void.class, Byte.class), fromInstance -> null);
        factory.put(pair(Byte.class, Byte.class), fromInstance -> fromInstance);
        factory.put(pair(Short.class, Byte.class), fromInstance -> ((Number) fromInstance).byteValue());
        factory.put(pair(Integer.class, Byte.class), fromInstance -> ((Number) fromInstance).byteValue());
        factory.put(pair(Long.class, Byte.class), fromInstance -> ((Number) fromInstance).byteValue());
        factory.put(pair(Float.class, Byte.class), fromInstance -> ((Number) fromInstance).byteValue());
        factory.put(pair(Double.class, Byte.class), fromInstance -> ((Number) fromInstance).byteValue());
        factory.put(pair(Boolean.class, Byte.class), fromInstance -> (Boolean) fromInstance ? BYTE_ONE : BYTE_ZERO);
        factory.put(pair(Character.class, Byte.class), fromInstance -> (byte) ((Character) fromInstance).charValue());
        factory.put(pair(Calendar.class, Byte.class), fromInstance -> ((Number)fromInstance).byteValue());
        factory.put(pair(AtomicBoolean.class, Byte.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? BYTE_ONE : BYTE_ZERO);
        factory.put(pair(AtomicInteger.class, Byte.class), fromInstance -> ((Number) fromInstance).byteValue());
        factory.put(pair(AtomicLong.class, Byte.class), fromInstance -> ((Number) fromInstance).byteValue());
        factory.put(pair(BigInteger.class, Byte.class), fromInstance -> ((Number) fromInstance).byteValue());
        factory.put(pair(BigDecimal.class, Byte.class), fromInstance -> ((Number) fromInstance).byteValue());
        factory.put(pair(Number.class, Byte.class), fromInstance -> ((Number)fromInstance).byteValue());
        factory.put(pair(Map.class, Byte.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, byte.class, null));
        factory.put(pair(String.class, Byte.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return BYTE_ZERO;
            }
            try {
                return Byte.valueOf(str);
            } catch (NumberFormatException e) {
                long value = convert(fromInstance, long.class);
                if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                    throw new IllegalArgumentException("Value: " + fromInstance + " outside " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE);
                }
                return (byte) value;
            }
        });

        // Short/short conversions supported
        factory.put(pair(Void.class, short.class), fromInstance -> (short)0);
        factory.put(pair(Void.class, Short.class), fromInstance -> null);
        factory.put(pair(Byte.class, Short.class), fromInstance -> ((Number) fromInstance).shortValue());
        factory.put(pair(Short.class, Short.class), fromInstance -> fromInstance);
        factory.put(pair(Integer.class, Short.class), fromInstance ->  ((Number) fromInstance).shortValue());
        factory.put(pair(Long.class, Short.class), fromInstance ->  ((Number) fromInstance).shortValue());
        factory.put(pair(Float.class, Short.class), fromInstance ->  ((Number) fromInstance).shortValue());
        factory.put(pair(Double.class, Short.class), fromInstance ->  ((Number) fromInstance).shortValue());
        factory.put(pair(Boolean.class, Short.class), fromInstance -> (Boolean) fromInstance ? SHORT_ONE : SHORT_ZERO);
        factory.put(pair(Character.class, Short.class), fromInstance -> (short) ((Character) fromInstance).charValue());
        factory.put(pair(AtomicBoolean.class, Short.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? SHORT_ONE : SHORT_ZERO);
        factory.put(pair(AtomicInteger.class, Short.class), fromInstance -> ((Number) fromInstance).shortValue());
        factory.put(pair(AtomicLong.class, Short.class), fromInstance -> ((Number) fromInstance).shortValue());
        factory.put(pair(BigInteger.class, Short.class), fromInstance -> ((Number) fromInstance).shortValue());
        factory.put(pair(BigDecimal.class, Short.class), fromInstance -> ((Number) fromInstance).shortValue());
        factory.put(pair(LocalDate.class, Short.class), fromInstance -> ((LocalDate)fromInstance).toEpochDay());
        factory.put(pair(Number.class, Short.class), fromInstance -> ((Number)fromInstance).shortValue());
        factory.put(pair(Map.class, Short.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, short.class, null));
        factory.put(pair(String.class, Short.class),fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return SHORT_ZERO;
            }
            try {
                return Short.valueOf(str);
            } catch (NumberFormatException e) {
                long value = convert(fromInstance, long.class);
                if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                    throw new NumberFormatException("Value: " + fromInstance + " outside " + Short.MIN_VALUE + " to " + Short.MAX_VALUE);
                }
                return (short) value;
            }
        });

        // Integer/int conversions supported
        factory.put(pair(Void.class, int.class), fromInstance -> 0);
        factory.put(pair(Void.class, Integer.class), fromInstance -> null);
        factory.put(pair(Byte.class, Integer.class), fromInstance -> ((Number) fromInstance).intValue());
        factory.put(pair(Short.class, Integer.class), fromInstance -> ((Number) fromInstance).intValue());
        factory.put(pair(Integer.class, Integer.class), fromInstance -> fromInstance);
        factory.put(pair(Long.class, Integer.class), fromInstance -> ((Number) fromInstance).intValue());
        factory.put(pair(Float.class, Integer.class), fromInstance ->  ((Number) fromInstance).intValue());
        factory.put(pair(Double.class, Integer.class), fromInstance ->  ((Number) fromInstance).intValue());
        factory.put(pair(Boolean.class, Integer.class), fromInstance -> (Boolean) fromInstance ? INTEGER_ONE : INTEGER_ZERO);
        factory.put(pair(Character.class, Integer.class), fromInstance -> (int) (Character) fromInstance);
        factory.put(pair(AtomicBoolean.class, Integer.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? INTEGER_ONE : INTEGER_ZERO);
        factory.put(pair(AtomicInteger.class, Integer.class), fromInstance -> ((Number) fromInstance).intValue());
        factory.put(pair(AtomicLong.class, Integer.class), fromInstance -> ((Number) fromInstance).intValue());
        factory.put(pair(BigInteger.class, Integer.class), fromInstance -> ((Number) fromInstance).intValue());
        factory.put(pair(BigDecimal.class, Integer.class), fromInstance -> ((Number) fromInstance).intValue());
        factory.put(pair(LocalDate.class, Integer.class), fromInstance -> (int)((LocalDate)fromInstance).toEpochDay());
        factory.put(pair(Number.class, Integer.class), fromInstance -> ((Number) fromInstance).intValue());
        factory.put(pair(Map.class, Integer.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, int.class, null));
        factory.put(pair(String.class, Integer.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return INTEGER_ZERO;
            }
            try {
                return Integer.valueOf(str);
            } catch (NumberFormatException e) {
                long value = convert(fromInstance, long.class);
                if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                    throw new NumberFormatException("Value: " + fromInstance + " outside " + Integer.MIN_VALUE + " to " + Integer.MAX_VALUE);
                }
                return (int) value;
            }
        });

        // Long/long conversions supported
        factory.put(pair(Void.class, long.class), fromInstance -> 0L);
        factory.put(pair(Void.class, Long.class), fromInstance -> null);
        factory.put(pair(Byte.class, Long.class), fromInstance -> ((Number) fromInstance).longValue());
        factory.put(pair(Short.class, Long.class), fromInstance ->  ((Number) fromInstance).longValue());
        factory.put(pair(Integer.class, Long.class), fromInstance ->  ((Number) fromInstance).longValue());
        factory.put(pair(Long.class, Long.class), fromInstance -> fromInstance);
        factory.put(pair(Float.class, Long.class), fromInstance ->  ((Number) fromInstance).longValue());
        factory.put(pair(Double.class, Long.class), fromInstance ->  ((Number) fromInstance).longValue());
        factory.put(pair(Boolean.class, Long.class), fromInstance -> (Boolean) fromInstance ? LONG_ONE : LONG_ZERO);
        factory.put(pair(Character.class, Long.class), fromInstance -> (long) ((char) fromInstance));
        factory.put(pair(AtomicBoolean.class, Long.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? LONG_ONE : LONG_ZERO);
        factory.put(pair(AtomicInteger.class, Long.class), fromInstance -> ((Number) fromInstance).longValue());
        factory.put(pair(AtomicLong.class, Long.class), fromInstance -> ((Number) fromInstance).longValue());
        factory.put(pair(BigInteger.class, Long.class), fromInstance -> ((Number) fromInstance).longValue());
        factory.put(pair(BigDecimal.class, Long.class), fromInstance -> ((Number) fromInstance).longValue());
        factory.put(pair(Date.class, Long.class), fromInstance -> ((Date) fromInstance).getTime());
        factory.put(pair(java.sql.Date.class, Long.class), fromInstance -> ((Date) fromInstance).getTime());
        factory.put(pair(Timestamp.class, Long.class), fromInstance -> ((Date) fromInstance).getTime());
        factory.put(pair(LocalDate.class, Long.class), fromInstance -> ((LocalDate) fromInstance).toEpochDay());
        factory.put(pair(LocalDateTime.class, Long.class), fromInstance -> localDateTimeToMillis((LocalDateTime) fromInstance));
        factory.put(pair(ZonedDateTime.class, Long.class), fromInstance -> zonedDateTimeToMillis((ZonedDateTime) fromInstance));
        factory.put(pair(Number.class, Long.class), fromInstance -> ((Number) fromInstance).longValue());
        factory.put(pair(Calendar.class, Long.class), fromInstance -> ((Calendar) fromInstance).getTime().getTime());
        factory.put(pair(Map.class, Long.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, long.class, null));
        factory.put(pair(String.class, Long.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return LONG_ZERO;
            }
            try {
                return Long.valueOf(str);
            } catch (NumberFormatException e) {
                return convert(fromInstance, BigDecimal.class).longValue();
            }
        });

        // Float/float conversions supported
        factory.put(pair(Void.class, float.class), fromInstance -> 0.0f);
        factory.put(pair(Void.class, Float.class), fromInstance -> null);
        factory.put(pair(Byte.class, Float.class), fromInstance -> ((Number) fromInstance).floatValue());
        factory.put(pair(Short.class, Float.class), fromInstance -> ((Number) fromInstance).floatValue());
        factory.put(pair(Integer.class, Float.class), fromInstance -> ((Number) fromInstance).floatValue());
        factory.put(pair(Long.class, Float.class), fromInstance -> ((Number) fromInstance).floatValue());
        factory.put(pair(Float.class, Float.class), fromInstance -> fromInstance);
        factory.put(pair(Double.class, Float.class), fromInstance ->  ((Number) fromInstance).floatValue());
        factory.put(pair(Boolean.class, Float.class), fromInstance -> (Boolean) fromInstance ? FLOAT_ONE : FLOAT_ZERO);
        factory.put(pair(Character.class, Float.class), fromInstance -> (float) ((char) fromInstance));
        factory.put(pair(LocalDate.class, Float.class), fromInstance -> ((LocalDate) fromInstance).toEpochDay());
        factory.put(pair(AtomicBoolean.class, Float.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? FLOAT_ONE : FLOAT_ZERO);
        factory.put(pair(AtomicInteger.class, Float.class), fromInstance -> ((Number) fromInstance).floatValue());
        factory.put(pair(AtomicLong.class, Float.class), fromInstance -> ((Number) fromInstance).floatValue());
        factory.put(pair(BigInteger.class, Float.class), fromInstance -> ((Number) fromInstance).floatValue());
        factory.put(pair(BigDecimal.class, Float.class), fromInstance -> ((Number) fromInstance).floatValue());
        factory.put(pair(Number.class, Float.class), fromInstance -> ((Number) fromInstance).floatValue());
        factory.put(pair(Map.class, Float.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, float.class, null));
        factory.put(pair(String.class, Float.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return FLOAT_ZERO;
            }
            try {
                return Float.valueOf(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(e.getMessage(), e.getCause());
            }
        });

        // Double/double conversions supported
        factory.put(pair(Void.class, double.class), fromInstance -> 0.0d);
        factory.put(pair(Void.class, Double.class), fromInstance -> null);
        factory.put(pair(Byte.class, Double.class), fromInstance -> ((Number) fromInstance).doubleValue());
        factory.put(pair(Short.class, Double.class), fromInstance -> ((Number) fromInstance).doubleValue());
        factory.put(pair(Integer.class, Double.class), fromInstance -> ((Number) fromInstance).doubleValue());
        factory.put(pair(Long.class, Double.class), fromInstance -> ((Number) fromInstance).doubleValue());
        factory.put(pair(Float.class, Double.class), fromInstance -> ((Number) fromInstance).doubleValue());
        factory.put(pair(Double.class, Double.class), fromInstance -> fromInstance);
        factory.put(pair(Boolean.class, Double.class), fromInstance -> (Boolean) fromInstance ? DOUBLE_ONE : DOUBLE_ZERO);
        factory.put(pair(Character.class, Double.class), fromInstance -> (double) ((char) fromInstance));
        factory.put(pair(LocalDate.class, Double.class), fromInstance -> (double)((LocalDate) fromInstance).toEpochDay());
        factory.put(pair(LocalDateTime.class, Double.class), fromInstance ->  (double) localDateTimeToMillis((LocalDateTime) fromInstance));
        factory.put(pair(ZonedDateTime.class, Double.class), fromInstance -> (double) zonedDateTimeToMillis((ZonedDateTime) fromInstance));
        factory.put(pair(Date.class, Double.class), fromInstance -> (double)((Date) fromInstance).getTime());
        factory.put(pair(java.sql.Date.class, Double.class), fromInstance -> (double)((Date) fromInstance).getTime());
        factory.put(pair(Timestamp.class, Double.class), fromInstance -> (double)((Date) fromInstance).getTime());
        factory.put(pair(AtomicBoolean.class, Double.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? DOUBLE_ONE : DOUBLE_ZERO);
        factory.put(pair(AtomicInteger.class, Double.class), fromInstance -> ((Number) fromInstance).doubleValue());
        factory.put(pair(AtomicLong.class, Double.class), fromInstance -> ((Number) fromInstance).doubleValue());
        factory.put(pair(BigInteger.class, Double.class), fromInstance -> ((Number) fromInstance).doubleValue());
        factory.put(pair(BigDecimal.class, Double.class), fromInstance -> ((Number) fromInstance).doubleValue());
        factory.put(pair(Calendar.class, Double.class), fromInstance -> (double)((Calendar) fromInstance).getTime().getTime());
        factory.put(pair(Number.class, Double.class), fromInstance -> ((Number) fromInstance).doubleValue());
        factory.put(pair(Map.class, Double.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, double.class, null));
        factory.put(pair(String.class, Double.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return DOUBLE_ZERO;
            }
            try {
                return Double.valueOf(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(e.getMessage(), e.getCause());
            }
        });

        // Boolean/boolean conversions supported
        factory.put(pair(Void.class, boolean.class), fromInstance -> false);
        factory.put(pair(Void.class, Boolean.class), fromInstance -> null);
        factory.put(pair(Byte.class, Boolean.class), fromInstance -> ((Number) fromInstance).byteValue() != 0);
        factory.put(pair(Short.class, Boolean.class), fromInstance -> ((Number) fromInstance).shortValue() != 0);
        factory.put(pair(Integer.class, Boolean.class), fromInstance -> ((Number) fromInstance).intValue() != 0);
        factory.put(pair(Long.class, Boolean.class), fromInstance -> ((Number) fromInstance).longValue() != 0);
        factory.put(pair(Float.class, Boolean.class), fromInstance -> ((Number) fromInstance).floatValue() != 0.0f);
        factory.put(pair(Double.class, Boolean.class), fromInstance -> ((Number) fromInstance).doubleValue() != 0.0d);
        factory.put(pair(Boolean.class, Boolean.class), fromInstance -> fromInstance);
        factory.put(pair(Character.class, Boolean.class), fromInstance -> ((char) fromInstance) > 0);
        factory.put(pair(AtomicBoolean.class, Boolean.class), fromInstance -> ((AtomicBoolean) fromInstance).get());
        factory.put(pair(AtomicInteger.class, Boolean.class), fromInstance -> ((Number) fromInstance).intValue() != 0);
        factory.put(pair(AtomicLong.class, Boolean.class), fromInstance -> ((Number) fromInstance).longValue() != 0);
        factory.put(pair(BigInteger.class, Boolean.class), fromInstance -> ((Number) fromInstance).longValue() != 0);
        factory.put(pair(BigDecimal.class, Boolean.class), fromInstance -> ((Number) fromInstance).longValue() != 0);
        factory.put(pair(Number.class, Boolean.class), fromInstance -> ((Number) fromInstance).longValue() != 0);
        factory.put(pair(Map.class, Boolean.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, boolean.class, null));
        factory.put(pair(String.class, Boolean.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return false;
            }
            // faster equals check "true" and "false"
            if ("true".equals(str)) {
                return true;
            } else if ("false".equals(str)) {
                return false;
            }
            return "true".equalsIgnoreCase(str);
        });

        // Character/chat conversions supported
        factory.put(pair(Void.class, char.class), fromInstance -> (char)0);
        factory.put(pair(Void.class, Character.class), fromInstance -> null);
        factory.put(pair(Byte.class, Character.class), fromInstance -> ((Number) fromInstance).byteValue() != 0 ? '1' : '0');
        factory.put(pair(Short.class, Character.class), fromInstance -> numberToCharacter((Number)fromInstance));
        factory.put(pair(Integer.class, Character.class), fromInstance -> numberToCharacter((Number)fromInstance));
        factory.put(pair(Long.class, Character.class), fromInstance -> numberToCharacter((Number)fromInstance));
        factory.put(pair(Float.class, Character.class), fromInstance -> numberToCharacter((Number)fromInstance));
        factory.put(pair(Double.class, Character.class), fromInstance -> numberToCharacter((Number)fromInstance));
        factory.put(pair(Boolean.class, Character.class), fromInstance -> ((Boolean)fromInstance) ? '1' : '0');
        factory.put(pair(Character.class, Character.class), fromInstance -> fromInstance);
        factory.put(pair(AtomicBoolean.class, Character.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? '1' : '0');
        factory.put(pair(AtomicInteger.class, Character.class), fromInstance -> numberToCharacter((Number)fromInstance));
        factory.put(pair(AtomicLong.class, Character.class), fromInstance -> numberToCharacter((Number)fromInstance));
        factory.put(pair(BigInteger.class, Character.class), fromInstance -> numberToCharacter((Number)fromInstance));
        factory.put(pair(BigDecimal.class, Character.class), fromInstance -> numberToCharacter((Number)fromInstance));
        factory.put(pair(Number.class, Character.class), fromInstance -> numberToCharacter((Number)fromInstance));
        factory.put(pair(Map.class, Character.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, char.class, null));
        factory.put(pair(String.class, Character.class), fromInstance -> {
            String str = ((String) fromInstance);
            if (str.isEmpty()) {
                return (char)0;
            }
            if (str.length() == 1) {
                return str.charAt(0);
            }
            // Treat as a String number, like "65" = 'A'
            return (char) Integer.parseInt(str.trim());
        });

        // BigInteger versions supported
        factory.put(pair(Void.class, BigInteger.class), fromInstance -> null);
        factory.put(pair(Byte.class, BigInteger.class), fromInstance -> BigInteger.valueOf((byte) fromInstance));
        factory.put(pair(Short.class, BigInteger.class), fromInstance -> BigInteger.valueOf((short)fromInstance));
        factory.put(pair(Integer.class, BigInteger.class), fromInstance -> BigInteger.valueOf((int)fromInstance));
        factory.put(pair(Long.class, BigInteger.class), fromInstance -> BigInteger.valueOf((long)fromInstance));
        factory.put(pair(Float.class, BigInteger.class), fromInstance -> new BigInteger(String.format("%.0f", (float)fromInstance)));
        factory.put(pair(Double.class, BigInteger.class), fromInstance -> new BigInteger(String.format("%.0f", (double)fromInstance)));
        factory.put(pair(Boolean.class, BigInteger.class), fromInstance -> (Boolean) fromInstance ? BigInteger.ONE : BigInteger.ZERO);
        factory.put(pair(Character.class, BigInteger.class), fromInstance -> BigInteger.valueOf(((char) fromInstance)));
        factory.put(pair(BigInteger.class, BigInteger.class), fromInstance -> fromInstance);
        factory.put(pair(BigDecimal.class, BigInteger.class), fromInstance -> ((BigDecimal)fromInstance).toBigInteger());
        factory.put(pair(AtomicBoolean.class, BigInteger.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? BigInteger.ONE : BigInteger.ZERO);
        factory.put(pair(AtomicInteger.class, BigInteger.class), fromInstance -> BigInteger.valueOf(((Number) fromInstance).intValue()));
        factory.put(pair(AtomicLong.class, BigInteger.class), fromInstance -> BigInteger.valueOf(((Number) fromInstance).longValue()));
        factory.put(pair(Date.class, BigInteger.class), fromInstance -> BigInteger.valueOf(((Date) fromInstance).getTime()));
        factory.put(pair(java.sql.Date.class, BigInteger.class), fromInstance -> BigInteger.valueOf(((Date) fromInstance).getTime()));
        factory.put(pair(Timestamp.class, BigInteger.class), fromInstance -> BigInteger.valueOf(((Date) fromInstance).getTime()));
        factory.put(pair(LocalDate.class, BigInteger.class), fromInstance -> BigInteger.valueOf(((LocalDate) fromInstance).toEpochDay()));
        factory.put(pair(LocalDateTime.class, BigInteger.class), fromInstance -> BigInteger.valueOf(localDateTimeToMillis((LocalDateTime) fromInstance)));
        factory.put(pair(ZonedDateTime.class, BigInteger.class), fromInstance -> BigInteger.valueOf(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        factory.put(pair(UUID.class, BigInteger.class), fromInstance -> {
            UUID uuid = (UUID) fromInstance;
            BigInteger mostSignificant = BigInteger.valueOf(uuid.getMostSignificantBits());
            BigInteger leastSignificant = BigInteger.valueOf(uuid.getLeastSignificantBits());
            // Shift the most significant bits to the left and add the least significant bits
            return mostSignificant.shiftLeft(64).add(leastSignificant);
        });
        factory.put(pair(Calendar.class, BigInteger.class), fromInstance -> BigInteger.valueOf(((Calendar) fromInstance).getTime().getTime()));
        factory.put(pair(Number.class, BigInteger.class), fromInstance -> new BigInteger(fromInstance.toString()));
        factory.put(pair(Map.class, BigInteger.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, BigInteger.class, null));
        factory.put(pair(String.class, BigInteger.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return null;
            }
            return new BigInteger(str);
        });

        // BigDecimal conversions supported
        factory.put(pair(Void.class, BigDecimal.class), fromInstance -> null);
        factory.put(pair(Byte.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        factory.put(pair(Short.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        factory.put(pair(Integer.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        factory.put(pair(Long.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        factory.put(pair(Float.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf((Float) fromInstance));
        factory.put(pair(Double.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf((Double) fromInstance));
        factory.put(pair(Boolean.class, BigDecimal.class), fromInstance -> (Boolean) fromInstance ? BigDecimal.ONE : BigDecimal.ZERO);
        factory.put(pair(Character.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((char) fromInstance)));
        factory.put(pair(BigDecimal.class, BigDecimal.class), fromInstance -> fromInstance);
        factory.put(pair(BigInteger.class, BigDecimal.class), fromInstance -> new BigDecimal((BigInteger) fromInstance));
        factory.put(pair(AtomicBoolean.class, BigDecimal.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? BigDecimal.ONE : BigDecimal.ZERO);
        factory.put(pair(AtomicInteger.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        factory.put(pair(AtomicLong.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        factory.put(pair(Date.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((Date) fromInstance).getTime()));
        factory.put(pair(java.sql.Date.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((Date) fromInstance).getTime()));
        factory.put(pair(Timestamp.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((Date) fromInstance).getTime()));
        factory.put(pair(LocalDate.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((LocalDate) fromInstance).toEpochDay()));
        factory.put(pair(LocalDateTime.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(localDateTimeToMillis((LocalDateTime) fromInstance)));
        factory.put(pair(ZonedDateTime.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        factory.put(pair(UUID.class, BigDecimal.class), fromInstance -> {
            UUID uuid = (UUID) fromInstance;
            BigInteger mostSignificant = BigInteger.valueOf(uuid.getMostSignificantBits());
            BigInteger leastSignificant = BigInteger.valueOf(uuid.getLeastSignificantBits());
            // Shift the most significant bits to the left and add the least significant bits
            return new BigDecimal(mostSignificant.shiftLeft(64).add(leastSignificant));
        });
        factory.put(pair(Calendar.class, BigDecimal.class), fromInstance -> BigDecimal.valueOf(((Calendar) fromInstance).getTime().getTime()));
        factory.put(pair(Number.class, BigDecimal.class), fromInstance -> new BigDecimal(fromInstance.toString()));
        factory.put(pair(Map.class, BigDecimal.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, BigDecimal.class, null));
        factory.put(pair(String.class, BigDecimal.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return null;
            }
            return new BigDecimal(str);
        });

        // AtomicBoolean conversions supported
        factory.put(pair(Void.class, AtomicBoolean.class), fromInstance -> null);
        factory.put(pair(Byte.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        factory.put(pair(Short.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        factory.put(pair(Integer.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        factory.put(pair(Long.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        factory.put(pair(Float.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        factory.put(pair(Double.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        factory.put(pair(Boolean.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean((Boolean) fromInstance));
        factory.put(pair(Character.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean((char) fromInstance > 0));
        factory.put(pair(BigInteger.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        factory.put(pair(BigDecimal.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        factory.put(pair(AtomicBoolean.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((AtomicBoolean) fromInstance).get()));  // mutable, so dupe
        factory.put(pair(AtomicInteger.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).intValue() != 0));
        factory.put(pair(AtomicLong.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        factory.put(pair(Number.class, AtomicBoolean.class), fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        factory.put(pair(Map.class, AtomicBoolean.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, AtomicBoolean.class, null));
        factory.put(pair(String.class, AtomicBoolean.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return null;
            }
            return new AtomicBoolean("true".equalsIgnoreCase(str));
        });

        // AtomicInteger conversions supported
        factory.put(pair(Void.class, AtomicInteger.class), fromInstance -> null);
        factory.put(pair(Byte.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((Number) fromInstance).intValue()));
        factory.put(pair(Short.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        factory.put(pair(Integer.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        factory.put(pair(Long.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        factory.put(pair(Float.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        factory.put(pair(Double.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        factory.put(pair(Boolean.class, AtomicInteger.class), fromInstance -> ((Boolean) fromInstance) ? new AtomicInteger(1) : new AtomicInteger(0));
        factory.put(pair(Character.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((char) fromInstance)));
        factory.put(pair(BigInteger.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        factory.put(pair(BigDecimal.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        factory.put(pair(AtomicInteger.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((Number)fromInstance).intValue())); // mutable, so dupe
        factory.put(pair(AtomicBoolean.class, AtomicInteger.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? new AtomicInteger(1) : new AtomicInteger(0));
        factory.put(pair(AtomicLong.class, AtomicInteger.class), fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        factory.put(pair(LocalDate.class, AtomicInteger.class), fromInstance -> new AtomicInteger((int)((LocalDate) fromInstance).toEpochDay()));
        factory.put(pair(Number.class, AtomicBoolean.class), fromInstance -> new AtomicInteger(((Number) fromInstance).intValue()));
        factory.put(pair(Map.class, AtomicInteger.class), fromInstance -> fromValueMap((Map<?, ?>)fromInstance, AtomicInteger.class, null));
        factory.put(pair(String.class, AtomicInteger.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return null;
            }
            return new AtomicInteger(Integer.parseInt(str));
        });

        // Class conversions supported
        factory.put(pair(Void.class, Class.class), fromInstance -> null);
        factory.put(pair(Class.class, Class.class), fromInstance -> fromInstance);
        factory.put(pair(Map.class, Class.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, Class.class, null));
        factory.put(pair(String.class, Class.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            Class<?> clazz = MetaUtils.classForName(str, Converter.class.getClassLoader());
            if (clazz != null) {
                return clazz;
            }
            throw new IllegalArgumentException("Cannot convert String '" + str + "' to class.  Class not found.");
        });

        // Convertable types
        toTypes.put(String.class, Converter::convertToString);
        toTypes.put(AtomicLong.class, Converter::convertToAtomicLong);
        toTypes.put(Date.class, Converter::convertToDate);
        toTypes.put(java.sql.Date.class, Converter::convertToSqlDate);
        toTypes.put(Timestamp.class, Converter::convertToTimestamp);
        toTypes.put(Calendar.class, Converter::convertToCalendar);
        toTypes.put(GregorianCalendar.class, Converter::convertToCalendar);
        toTypes.put(LocalDate.class, Converter::convertToLocalDate);
        toTypes.put(LocalDateTime.class, Converter::convertToLocalDateTime);
        toTypes.put(ZonedDateTime.class, Converter::convertToZonedDateTime);
        toTypes.put(UUID.class, Converter::convertToUUID);
        toTypes.put(Map.class, Converter::convertToMap);

        // ? to Date
        toDate.put(Map.class, null);
        toDate.put(Date.class, fromInstance -> new Date(((Date) fromInstance).getTime()));  // Date is mutable
        toDate.put(java.sql.Date.class, fromInstance -> new Date(((Date) fromInstance).getTime()));
        toDate.put(Timestamp.class, fromInstance -> new Date(((Date) fromInstance).getTime()));
        toDate.put(LocalDate.class, fromInstance -> new Date(localDateToMillis((LocalDate) fromInstance)));
        toDate.put(LocalDateTime.class, fromInstance -> new Date(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toDate.put(ZonedDateTime.class, fromInstance -> new Date(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toDate.put(GregorianCalendar.class, fromInstance -> new Date(((Calendar)fromInstance).getTime().getTime()));
        toDate.put(Long.class, fromInstance -> new Date((long) fromInstance));
        toDate.put(Double.class, fromInstance -> new Date(((Number) fromInstance).longValue()));
        toDate.put(BigInteger.class, fromInstance -> new Date(((Number) fromInstance).longValue()));
        toDate.put(BigDecimal.class, fromInstance -> new Date(((Number) fromInstance).longValue()));
        toDate.put(AtomicLong.class, fromInstance -> new Date(((Number) fromInstance).longValue()));
        toDate.put(String.class, fromInstance -> DateUtilities.parseDate(((String) fromInstance).trim()));
        targetTypes.put(Date.class, toDate);

        // ? to java.sql.Date
        toSqlDate.put(Map.class, null);
        toSqlDate.put(java.sql.Date.class, fromInstance -> new java.sql.Date(((java.sql.Date) fromInstance).getTime()));  // java.sql.Date is mutable
        toSqlDate.put(Date.class, fromInstance -> new java.sql.Date(((Date) fromInstance).getTime()));
        toSqlDate.put(Timestamp.class, fromInstance -> new java.sql.Date(((Date) fromInstance).getTime()));
        toSqlDate.put(LocalDate.class, fromInstance -> new java.sql.Date(localDateToMillis((LocalDate) fromInstance)));
        toSqlDate.put(LocalDateTime.class, fromInstance -> new java.sql.Date(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toSqlDate.put(ZonedDateTime.class, fromInstance -> new java.sql.Date(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toSqlDate.put(GregorianCalendar.class, fromInstance -> new java.sql.Date(((Calendar)fromInstance).getTime().getTime()));
        toSqlDate.put(Long.class, fromInstance -> new java.sql.Date((long) fromInstance));
        toSqlDate.put(Double.class, fromInstance -> new java.sql.Date(((Number) fromInstance).longValue()));
        toSqlDate.put(BigInteger.class, fromInstance -> new java.sql.Date(((Number) fromInstance).longValue()));
        toSqlDate.put(BigDecimal.class, fromInstance -> new java.sql.Date(((Number) fromInstance).longValue()));
        toSqlDate.put(AtomicLong.class, fromInstance -> new java.sql.Date(((Number) fromInstance).longValue()));
        toSqlDate.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return new java.sql.Date(date.getTime());
        });
        targetTypes.put(java.sql.Date.class, toSqlDate);

        // ? to Timestamp
        toTimestamp.put(Map.class, null);
        toTimestamp.put(Timestamp.class, fromInstance -> new Timestamp(((Timestamp)fromInstance).getTime()));  // Timestamp is mutable
        toTimestamp.put(java.sql.Date.class, fromInstance -> new Timestamp(((Date) fromInstance).getTime()));
        toTimestamp.put(Date.class, fromInstance -> new Timestamp(((Date) fromInstance).getTime()));
        toTimestamp.put(LocalDate.class, fromInstance -> new Timestamp(localDateToMillis((LocalDate) fromInstance)));
        toTimestamp.put(LocalDateTime.class, fromInstance -> new Timestamp(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toTimestamp.put(ZonedDateTime.class, fromInstance -> new Timestamp(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toTimestamp.put(GregorianCalendar.class, fromInstance -> new Timestamp(((Calendar)fromInstance).getTime().getTime()));
        toTimestamp.put(Long.class, fromInstance -> new Timestamp((long) fromInstance));
        toTimestamp.put(Double.class, fromInstance -> new Timestamp(((Number) fromInstance).longValue()));
        toTimestamp.put(BigInteger.class, fromInstance -> new Timestamp(((Number) fromInstance).longValue()));
        toTimestamp.put(BigDecimal.class, fromInstance -> new Timestamp(((Number) fromInstance).longValue()));
        toTimestamp.put(AtomicLong.class, fromInstance -> new Timestamp(((Number) fromInstance).longValue()));
        toTimestamp.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return new Timestamp(date.getTime());
        });
        targetTypes.put(Timestamp.class, toTimestamp);

        // ? to Calendar
        toCalendar.put(Map.class, null);
        toCalendar.put(GregorianCalendar.class, fromInstance -> ((Calendar) fromInstance).clone());
        toCalendar.put(Date.class, fromInstance -> initCal(((Date) fromInstance).getTime()));
        toCalendar.put(java.sql.Date.class, fromInstance -> initCal(((Date) fromInstance).getTime()));
        toCalendar.put(Timestamp.class, fromInstance -> initCal(((Date) fromInstance).getTime()));
        toCalendar.put(LocalDate.class, fromInstance -> initCal(localDateToMillis((LocalDate)fromInstance)));
        toCalendar.put(LocalDateTime.class, fromInstance -> initCal(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toCalendar.put(ZonedDateTime.class, fromInstance -> initCal(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toCalendar.put(Long.class, fromInstance -> initCal((Long)fromInstance));
        toCalendar.put(Double.class, fromInstance -> initCal(((Number) fromInstance).longValue()));
        toCalendar.put(BigInteger.class, fromInstance -> initCal(((Number) fromInstance).longValue()));
        toCalendar.put(BigDecimal.class, fromInstance -> initCal(((Number) fromInstance).longValue()));
        toCalendar.put(AtomicLong.class, fromInstance -> initCal(((Number) fromInstance).longValue()));
        toCalendar.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return initCal(date.getTime());
        });
        targetTypes.put(Calendar.class, toCalendar);
        targetTypes.put(GregorianCalendar.class, toCalendar);

        // ? to LocalDate
        toLocalDate.put(Map.class, null);
        toLocalDate.put(LocalDate.class, fromInstance -> fromInstance);
        toLocalDate.put(LocalDateTime.class, fromInstance -> ((LocalDateTime) fromInstance).toLocalDate());
        toLocalDate.put(ZonedDateTime.class, fromInstance -> ((ZonedDateTime) fromInstance).toLocalDate());
        toLocalDate.put(GregorianCalendar.class, fromInstance -> ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        toLocalDate.put(java.sql.Date.class, fromInstance -> ((java.sql.Date) fromInstance).toLocalDate());
        toLocalDate.put(Timestamp.class, fromInstance -> ((Timestamp) fromInstance).toLocalDateTime().toLocalDate());
        toLocalDate.put(Date.class, fromInstance -> ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        toLocalDate.put(Short.class, fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        toLocalDate.put(Integer.class, fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        toLocalDate.put(Long.class, fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        toLocalDate.put(AtomicInteger.class, fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        toLocalDate.put(AtomicLong.class, fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        toLocalDate.put(Float.class, fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        toLocalDate.put(Double.class, fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        toLocalDate.put(BigInteger.class, fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        toLocalDate.put(BigDecimal.class, fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        toLocalDate.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        });
        targetTypes.put(LocalDate.class, toLocalDate);

        // ? to LocalDateTime
        toLocalDateTime.put(Map.class, null);
        toLocalDateTime.put(LocalDateTime.class, fromInstance -> fromInstance);
        toLocalDateTime.put(LocalDate.class, fromInstance -> ((LocalDate)fromInstance).atStartOfDay());
        toLocalDateTime.put(ZonedDateTime.class, fromInstance -> ((ZonedDateTime) fromInstance).toLocalDateTime());
        toLocalDateTime.put(GregorianCalendar.class, fromInstance -> ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        toLocalDateTime.put(Timestamp.class, fromInstance -> ((Timestamp) fromInstance).toLocalDateTime());
        toLocalDateTime.put(Date.class, fromInstance -> ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        toLocalDateTime.put(Long.class, fromInstance -> Instant.ofEpochMilli((Long) fromInstance).atZone(ZoneId.systemDefault()).toLocalDateTime());
        toLocalDateTime.put(AtomicLong.class, fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        toLocalDateTime.put(Double.class, fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        toLocalDateTime.put(BigInteger.class, fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        toLocalDateTime.put(BigDecimal.class, fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        toLocalDateTime.put(java.sql.Date.class, fromInstance -> ((java.sql.Date)fromInstance).toLocalDate().atStartOfDay());
        toLocalDateTime.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        });
        targetTypes.put(LocalDateTime.class, toLocalDateTime);

        // ? to ZonedDateTime
        toZonedDateTime.put(Map.class, null);
        toZonedDateTime.put(ZonedDateTime.class, fromInstance -> fromInstance);
        toZonedDateTime.put(LocalDateTime.class, fromInstance -> ((LocalDateTime) fromInstance).atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(LocalDate.class, fromInstance -> ((LocalDate) fromInstance).atStartOfDay(ZoneId.systemDefault()));
        toZonedDateTime.put(GregorianCalendar.class, fromInstance -> ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(Timestamp.class, fromInstance -> ((Timestamp) fromInstance).toInstant().atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(Date.class, fromInstance -> ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(Long.class, fromInstance -> Instant.ofEpochMilli((Long) fromInstance).atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(AtomicLong.class, fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(Double.class, fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(BigInteger.class, fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(BigDecimal.class, fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(java.sql.Date.class, fromInstance -> ((java.sql.Date) fromInstance).toLocalDate().atStartOfDay(ZoneId.systemDefault()));
        toZonedDateTime.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return date.toInstant().atZone(ZoneId.systemDefault());
        });
        targetTypes.put(ZonedDateTime.class, toZonedDateTime);
        
        // ? to AtomicLong
        toAtomicLong.put(Map.class, null);
        toAtomicLong.put(AtomicLong.class, fromInstance -> new AtomicLong(((Number) fromInstance).longValue()));   // mutable, so dupe
        toAtomicLong.put(AtomicInteger.class, fromInstance -> new AtomicLong(((Number) fromInstance).longValue()));
        toAtomicLong.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? new AtomicLong(1) : new AtomicLong(0));
        toAtomicLong.put(BigInteger.class, fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        toAtomicLong.put(BigDecimal.class, fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        toAtomicLong.put(Short.class, fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        toAtomicLong.put(Integer.class, fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        toAtomicLong.put(Long.class, fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        toAtomicLong.put(Float.class, fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        toAtomicLong.put(Double.class, fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        toAtomicLong.put(Boolean.class, fromInstance -> ((Boolean) fromInstance) ? new AtomicLong(1) : new AtomicLong(0));
        toAtomicLong.put(Byte.class, fromInstance -> new AtomicLong(((Number) fromInstance).longValue()));
        toAtomicLong.put(Character.class, fromInstance -> new AtomicLong(((char) fromInstance)));
        toAtomicLong.put(Date.class, fromInstance -> new AtomicLong(((Date) fromInstance).getTime()));
        toAtomicLong.put(java.sql.Date.class, fromInstance -> new AtomicLong(((Date) fromInstance).getTime()));
        toAtomicLong.put(Timestamp.class, fromInstance -> new AtomicLong(((Date) fromInstance).getTime()));
        toAtomicLong.put(LocalDate.class, fromInstance -> new AtomicLong(((LocalDate) fromInstance).toEpochDay()));
        toAtomicLong.put(LocalDateTime.class, fromInstance -> new AtomicLong(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toAtomicLong.put(ZonedDateTime.class, fromInstance -> new AtomicLong(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toAtomicLong.put(GregorianCalendar.class, fromInstance -> new AtomicLong(((Calendar) fromInstance).getTime().getTime()));
        toAtomicLong.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return null;
            }
            try {
                return new AtomicLong(Long.parseLong(str));
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException(e.getMessage(), e.getCause());
            }
        });
        targetTypes.put(AtomicLong.class, toAtomicLong);

        // ? to UUID
        toUUID.put(Map.class, null);
        toUUID.put(UUID.class, fromInstance -> fromInstance);
        toUUID.put(String.class, fromInstance -> UUID.fromString(((String)fromInstance).trim()));
        toUUID.put(BigInteger.class, fromInstance -> {
            BigInteger bigInteger = (BigInteger) fromInstance;
            BigInteger mask = BigInteger.valueOf(Long.MAX_VALUE);
            long mostSignificantBits = bigInteger.shiftRight(64).and(mask).longValue();
            long leastSignificantBits = bigInteger.and(mask).longValue();
            return new UUID(mostSignificantBits, leastSignificantBits);
        });
        toUUID.put(BigDecimal.class, fromInstance -> {
            BigDecimal bigDecimal = (BigDecimal) fromInstance;
            BigInteger bigInteger = bigDecimal.toBigInteger();
            Convert<?> converter = toUUID.get(BigInteger.class);
            return converter.convert(bigInteger);
        });
        targetTypes.put(UUID.class, toUUID);

        // ? to String
        toStr.put(Map.class, null);
        toStr.put(String.class, fromInstance -> fromInstance);
        toStr.put(Boolean.class, Object::toString);
        toStr.put(AtomicBoolean.class, Object::toString);
        toStr.put(Byte.class, Object::toString);
        toStr.put(Short.class, Object::toString);
        toStr.put(Integer.class, Object::toString);
        toStr.put(AtomicInteger.class, Object::toString);
        toStr.put(BigInteger.class, Object::toString);
        toStr.put(Long.class, Object::toString);
        toStr.put(AtomicLong.class, Object::toString);
        toStr.put(Double.class, fromInstance -> new DecimalFormat("#.####################").format((double)fromInstance));
        toStr.put(BigDecimal.class, fromInstance -> ((BigDecimal)fromInstance).stripTrailingZeros().toPlainString());
        toStr.put(Float.class, fromInstance -> new DecimalFormat("#.####################").format((float)fromInstance));
        toStr.put(Class.class, fromInstance -> ((Class<?>) fromInstance).getName());
        toStr.put(UUID.class, Object::toString);
        toStr.put(GregorianCalendar.class, fromInstance -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Calendar) fromInstance).getTime());
        });
        toStr.put(Date.class, fromInstance -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Date) fromInstance));
        });
        toStr.put(java.sql.Date.class, fromInstance -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Date) fromInstance));
        });
        toStr.put(Timestamp.class, fromInstance -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Date) fromInstance));
        });
        toStr.put(Character.class, fromInstance -> "" + fromInstance);
        toStr.put(LocalDate.class, fromInstance -> {
            LocalDate localDate = (LocalDate) fromInstance;
            return String.format("%04d-%02d-%02d", localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        });
        toStr.put(LocalDateTime.class, fromInstance -> {
            LocalDateTime localDateTime = (LocalDateTime) fromInstance;
            return String.format("%04d-%02d-%02dT%02d:%02d:%02d", localDateTime.getYear(), localDateTime.getMonthValue(), localDateTime.getDayOfMonth(), localDateTime.getHour(), localDateTime.getMinute(), localDateTime.getSecond());
        });
        toStr.put(ZonedDateTime.class, fromInstance -> {
            ZonedDateTime zonedDateTime = (ZonedDateTime) fromInstance;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
            return zonedDateTime.format(formatter);
        });
        targetTypes.put(String.class, toStr);

        // ? to Map
        toMap.put(Map.class, null); // Can't have Map instance
        toMap.put(Byte.class, Converter::initMap);
        toMap.put(Short.class, Converter::initMap);
        toMap.put(Integer.class, Converter::initMap);
        toMap.put(Long.class, Converter::initMap);
        toMap.put(Float.class, Converter::initMap);
        toMap.put(Double.class, Converter::initMap);
        toMap.put(Boolean.class, Converter::initMap);
        toMap.put(Character.class, Converter::initMap);
        toMap.put(BigInteger.class, Converter::initMap);
        toMap.put(BigDecimal.class, Converter::initMap);
        toMap.put(AtomicBoolean.class, Converter::initMap);
        toMap.put(AtomicInteger.class, Converter::initMap);
        toMap.put(AtomicLong.class, Converter::initMap);
        toMap.put(Class.class, Converter::initMap);
        toMap.put(UUID.class, Converter::initMap);
        toMap.put(Calendar.class, Converter::initMap);
        toMap.put(GregorianCalendar.class, Converter::initMap);
        toMap.put(Date.class, Converter::initMap);
        toMap.put(java.sql.Date.class, Converter::initMap);
        toMap.put(Timestamp.class, Converter::initMap);
        toMap.put(LocalDate.class, Converter::initMap);
        toMap.put(LocalDateTime.class, Converter::initMap);
        toMap.put(ZonedDateTime.class, Converter::initMap);
        targetTypes.put(Map.class, toMap);
    }

    /**
     * Static utility class.
     */
    private Converter() {
    }

    private static Set<Class<?>> getSuperClassesAndInterfaces(Class<?> clazz) {
        Set<Class<?>> parentTypes = cacheParentTypes.get(clazz);
        if (parentTypes != null) {
            return parentTypes;
        }
        parentTypes = new ConcurrentSkipListSet<>(Comparator.comparing(Class::getName));
        addSuperClassesAndInterfaces(clazz, parentTypes);
        cacheParentTypes.put(clazz, parentTypes);
        return parentTypes;
    }

    private static void addSuperClassesAndInterfaces(Class<?> clazz, Set<Class<?>> result) {
        // Add all superinterfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            result.add(iface);
            addSuperClassesAndInterfaces(iface, result);
        }

        // Add superclass
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            result.add(superClass);
            addSuperClassesAndInterfaces(superClass, result);
        }
    }

    /**
     * Turn the passed in value to the class indicated.  This will allow, for
     * example, a String to be passed in and be converted to a Long.
     * <pre>
     *     Examples:
     *     Long x = convert("35", Long.class);
     *     Date d = convert("2015/01/01", Date.class)
     *     int y = convert(45.0, int.class)
     *     String date = convert(date, String.class)
     *     String date = convert(calendar, String.class)
     *     Short t = convert(true, short.class);     // returns (short) 1 or  (short) 0
     *     Long date = convert(calendar, long.class); // get calendar's time into long
     *     Map containing ["_v": "75.0"]
     *     convert(map, double.class)   // Converter will extract the value associated to the "_v" (or "value") key and convert it.
     * </pre>
     *
     * @param fromInstance A value used to create the targetType, even though it may
     *                     not (most likely will not) be the same data type as the targetType
     * @param toType       Class which indicates the targeted (final) data type.
     *                     Please note that in addition to the 8 Java primitives, the targeted class
     *                     can also be Date.class, String.class, BigInteger.class, BigDecimal.class, and
     *                     many other JDK classes, including Map.  For Map, often it will seek a 'value'
     *                     field, however, for some complex objects, like UUID, it will look for specific
     *                     fields within the Map to perform the conversion.
     * @return An instanceof targetType class, based upon the value passed in.
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(Object fromInstance, Class<T> toType) {
        if (toType == null) {
            throw new IllegalArgumentException("toType cannot be null");
        }
        Class<?> sourceType;
        if (fromInstance == null) {
            // Do not promote primitive to primitive wrapper - allows for different 'from NULL' type for each.
            sourceType = Void.class;
        } else {
            // Promote primitive to primitive wrapper so we don't have to define so many duplicates in the factory map.
            sourceType = fromInstance.getClass();
            if (toType.isPrimitive()) {
                toType = (Class<T>) toPrimitiveWrapperClass(toType);
            }
        }

        // Direct Mapping
        Convert<?> converter = factory.get(pair(sourceType, toType));
        if (converter != null) {
            return (T) converter.convert(fromInstance);
        }

        // Try inheritance
        Set<Class<?>> parentTypes = getSuperClassesAndInterfaces(sourceType);
        for (Class<?> clazz : parentTypes) {
            converter = factory.get(pair(clazz, toType));
            if (converter != null) {
                return (T) converter.convert(fromInstance);
            }
        }

        // Will be removing this code.
        converter = toTypes.get(toType);
        if (converter != null) {
            if ((fromInstance == null || isEmptyMap(fromInstance)) && fromNull.containsKey(toType)) {
                return (T) fromNull.get(toType);
            }
            
            try {
                Object value = converter.convert(fromInstance);
                if (value != NOPE) {
                    return (T) value;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Value [" + name(fromInstance) + "] could not be converted to a '" + getShortName(toType) + "'", e);
            }
        } else {
            return (T) convertUsingUserDefined(fromInstance, toType);
        }

        throw new IllegalArgumentException("Unsupported conversion, source type [" + name(fromInstance) + "] target type '" + getShortName(toType) + "'");
    }

    private static boolean isEmptyMap(Object fromInstance) {
        return fromInstance instanceof Map && ((Map)fromInstance).isEmpty();
    }

    private static String getShortName(Class<?> type) {
        return java.sql.Date.class.equals(type) ? type.getName() : type.getSimpleName();
    }

    private static Object convertToString(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toStr.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            return fromValueMap((Map<?, ?>) fromInstance, String.class, null);
        } else if (fromInstance instanceof Enum) {
            return ((Enum<?>) fromInstance).name();
        } else if (fromInstance instanceof Number) {
            return fromInstance.toString();
        } else if (fromInstance instanceof Calendar) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Calendar) fromInstance).getTime());
        }
        return NOPE;
    }

    private static Object convertToMap(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toMap.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            return fromValueMap((Map<?, ?>) fromInstance, String.class, null);
        } else if (fromInstance instanceof Enum) {
            return ((Enum<?>) fromInstance).name();
        } else if (fromInstance instanceof Number) {
            return fromInstance.toString();
        } else if (fromInstance instanceof Calendar) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Calendar) fromInstance).getTime());
        }
        return NOPE;
    }

    private static Object convertToUUID(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toUUID.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            Object ret = fromMap(map, "mostSigBits", long.class);
            if (ret != NOPE)
            {
                Object ret2 = fromMap(map, "leastSigBits", long.class);
                if (ret2 != NOPE) {
                    return new UUID((Long)ret, (Long)ret2);
                }
            }
            throw new IllegalArgumentException("To convert Map to UUID, the Map must contain both 'mostSigBits' and 'leastSigBits' keys");
        }
        return NOPE;
    }

    private static Object convertToSqlDate(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toSqlDate.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("time")) {
                return convert(map.get("time"), java.sql.Date.class);
            } else {
                return fromValueMap((Map<?,?>) fromInstance, java.sql.Date.class, MetaUtils.setOf("time"));
            }
        } else if (fromInstance instanceof Number) {
            return new java.sql.Date(((Number)fromInstance).longValue());
        }
        else if (fromInstance instanceof Calendar) {
            return new java.sql.Date(((Calendar) fromInstance).getTime().getTime());
        }
        return NOPE;
    }

    private static Object convertToTimestamp(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toTimestamp.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("time")) {
                long time = convert(map.get("time"), long.class);
                int ns = convert(map.get("nanos"), int.class);
                Timestamp timeStamp = new Timestamp(time);
                timeStamp.setNanos(ns);
                return timeStamp;
            } else {
                return fromValueMap(map, Timestamp.class, MetaUtils.setOf("time", "nanos"));
            }
        }
        else if (fromInstance instanceof Number) {
            return new Timestamp(((Number)fromInstance).longValue());
        }
        else if (fromInstance instanceof Calendar) {
            return new Timestamp(((Calendar) fromInstance).getTime().getTime());
        }
        return NOPE;
    }
    
    private static Object convertToDate(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toDate.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Calendar) {
            return ((Calendar) fromInstance).getTime();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("time")) {
                return convert(map.get("time"), Date.class);
            } else {
                return fromValueMap(map, Date.class, MetaUtils.setOf("time"));
            }
        }
        return NOPE;
    }

    private static Object convertToLocalDate(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toLocalDate.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Calendar) {
            return ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("month") && map.containsKey("day") && map.containsKey("year")) {
                int month = convert(map.get("month"), int.class);
                int day = convert(map.get("day"), int.class);
                int year = convert(map.get("year"), int.class);
                return LocalDate.of(year, month, day);
            } else {
                return fromValueMap(map, LocalDate.class, MetaUtils.setOf("year", "month", "day"));
            }
        }
        return NOPE;
    }

    private static Object convertToLocalDateTime(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toLocalDateTime.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Calendar) {
            return ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, LocalDateTime.class, null);
        }
        return NOPE;
    }
    
    private static Object convertToZonedDateTime(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toZonedDateTime.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Calendar) {
            return ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault());
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, ZonedDateTime.class, null);
        }
        return NOPE;
    }

    private static Object convertToCalendar(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toCalendar.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("time")) {
                Object zoneRaw = map.get("zone");
                TimeZone tz;
                if (zoneRaw instanceof String) {
                    String zone = (String) zoneRaw;
                    tz = TimeZone.getTimeZone(zone);
                } else {
                    tz = TimeZone.getDefault();
                }
                Calendar cal = Calendar.getInstance();
                cal.setTimeZone(tz);
                Date epochInMillis = convert(map.get("time"), Date.class);
                cal.setTimeInMillis(epochInMillis.getTime());
                return cal;
            } else {
                return fromValueMap(map, Calendar.class, MetaUtils.setOf("time", "zone"));
            }
        }
        return NOPE;
    }

    private static Object convertToAtomicLong(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toAtomicLong.get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, AtomicLong.class, null);
        } else if (fromInstance instanceof Number) {
            return new AtomicLong(((Number) fromInstance).longValue());
        } else if (fromInstance instanceof Calendar) {
            return new AtomicLong(((Calendar) fromInstance).getTime().getTime());
        }
        return NOPE;
    }
    
    private static String name(Object fromInstance) {
        if (fromInstance == null) {
            return "null";
        }
        return getShortName(fromInstance.getClass()) + " (" + fromInstance + ")";
    }

    private static Calendar initCal(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.setTimeInMillis(ms);
        return cal;
    }

    private static Map<String, ?> initMap(Object fromInstance)
    {
        Map<String, Object> map = new HashMap<>();
        map.put(VALUE, fromInstance);
        return map;
    }

    private static Object fromValueMap(Map<?, ?> map, Class<?> type, Set<String> set) {
        Object ret = fromMap(map, VALUE, type);
        if (ret != NOPE) {
            return ret;
        }

        ret = fromMap(map, VALUE2, type);
        if (ret == NOPE)
        {
            if (set == null || set.isEmpty()) {
                throw new IllegalArgumentException("To convert from Map to " + getShortName(type) + ", the map must include keys: '_v' or 'value' an associated value to convert from.");
            } else {
                throw new IllegalArgumentException("To convert from Map to " + getShortName(type) + ", the map must include keys: " + set + ", or '_v' or 'value' an associated value to convert from.");
            }
        }
        return ret;
    }
    
    private static Object fromMap(Map<?, ?> map, String key, Class<?> type) {
        if (map.containsKey(key)) {
            return convert(map.get(key), type);
        }
        return NOPE;
    }

    /**
     * Check to see if a conversion from type to another type is supported.
     * @param source Class of source type.
     * @param target Class of target type.
     * @return boolean true if the Converter converts from the source type to the destination type, false otherwise.
     */
    public static boolean isConversionSupportedFor(Class<?> source, Class<?> target) {
        // Conversion from the source instance passes through an Object parameter convert(fromInstance, target) and
        // therefore we do not need to define int.class, Integer.class in the Map for the targets.
        if (source.isPrimitive()) {
            source = toPrimitiveWrapperClass(source);
        }
        if (target.isPrimitive()) {
            target = toPrimitiveWrapperClass(target);
        }

        Map.Entry<Class<?>, Class<?>> key = new AbstractMap.SimpleImmutableEntry<>(source, target);
        if (userDefined.containsKey(key)) {
            return true;
        }

        if (!toTypes.containsKey(target)) {
            return false;
        }
        
        Map<Class<?>, Convert<?>> targets = Converter.targetTypes.get(target);
        return targets.containsKey(source);
    }

    /**
     * @return Map<Class, Set<Class>> which contains all supported conversions. The key of the Map is a source class,
     * and the Set contains all the target types (classes) that the source can be converted to.
     */
    public static Map<Class<?>, Set<Class<?>>> allSupportedConversions() {
        Map<Class<?>, Set<Class<?>>> toFrom = new TreeMap<>((c1, c2) -> getShortName(c1).compareToIgnoreCase(getShortName(c2)));

        for (Class<?> targetClass : toTypes.keySet()) {
            Map<Class<?>, Convert<?>> map = targetTypes.get(targetClass);
            Set<Class<?>> fromSet = new TreeSet<>((c1, c2) -> getShortName(c1).compareToIgnoreCase(getShortName(c2)));
            fromSet.addAll(map.keySet());
            toFrom.put(targetClass, fromSet);
        }

        // Add in user-defined
        for (Map.Entry<Class<?>, Class<?>> srcTargetPair : userDefined.keySet()) {
            if (toFrom.containsKey(srcTargetPair.getKey())) {
                Set<Class<?>> fromSet = toFrom.get(srcTargetPair.getKey());
                fromSet.add(srcTargetPair.getValue());
            } else {
                Set<Class<?>> fromSet = new TreeSet<>((c1, c2) -> getShortName(c1).compareToIgnoreCase(getShortName(c2)));
                fromSet.add(srcTargetPair.getValue());
                toFrom.put(srcTargetPair.getKey(), fromSet);
            }
        }
        
        return toFrom;
    }

    /**
     * @return Map<String, Set<String>> which contains all supported conversions. The key of the Map is a source class
     * name, and the Set contains all the target class names that the source can be converted to.
     */
    public static Map<String, Set<String>> getSupportedConversions() {
        Map<String, Set<String>> toFrom = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Class<?> targetClass : toTypes.keySet()) {
            Map<Class<?>, Convert<?>> fromMap = targetTypes.get(targetClass);

            for (Class<?> fromClass : fromMap.keySet()) {
                toFrom.computeIfAbsent(getShortName(targetClass), k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(getShortName(fromClass));
            }
        }

        // Add in user-defined
        for (Map.Entry<Class<?>, Class<?>> srcTargetPair : userDefined.keySet()) {
            String srcTypeName = getShortName(srcTargetPair.getKey());
            String targetTypeName = getShortName(srcTargetPair.getValue());

            if (toFrom.containsKey(srcTypeName)) {
                Set<String> fromSet = toFrom.get(srcTypeName);
                fromSet.add(targetTypeName);
            } else {
                Set<String> fromSet = new TreeSet<>(String::compareToIgnoreCase);
                fromSet.add(targetTypeName);
                toFrom.put(srcTypeName, fromSet);
            }
        }

        return toFrom;
    }

    public static void addConversion(Class<?> source, Class<?> target, Convert<?> conversionFunction) {
        if (toTypes.containsKey(target)) {
            Map<Class<?>, Convert<?>> map = targetTypes.get(target);
            if (map.containsKey(source)) {
                // Can't override built-in conversions.
                throw new IllegalArgumentException("A conversion for: " + getShortName(source) + " to: " + getShortName(target) + " already exists");
            }
            map.put(source, conversionFunction);
        } else {
            userDefined.put(new AbstractMap.SimpleImmutableEntry<>(source, target), conversionFunction);
        }
    }

    private static Object convertUsingUserDefined(Object fromInstance, Class<?> toType) {
        Class<?> sourceType;
        if (fromInstance == null) {
            sourceType = Void.class;
        } else {
            sourceType = fromInstance.getClass();
        }

        Map.Entry<Class<?>, Class<?>> key = new AbstractMap.SimpleImmutableEntry<>(sourceType, toType);
        Convert<?> converter = userDefined.get(key);
        if (converter == null) {
            throw new IllegalArgumentException("Unsupported target type '" + getShortName(toType) + "' requested for conversion from [" + name(fromInstance) + "]");
        }
        return converter.convert(fromInstance);
    }

    /**
     * @param localDate A Java LocalDate
     * @return a long representing the localDate as the number of millis since the epoch, Jan 1, 1970
     */
    static long localDateToMillis(LocalDate localDate) {
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * @param localDateTime A Java LocalDateTime
     * @return a long representing the localDateTime as the number of milliseconds since the
     * number of milliseconds since Jan 1, 1970
     */
    static long localDateTimeToMillis(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * @param zonedDateTime A Java ZonedDateTime
     * @return a long representing the zonedDateTime as the number of milliseconds since the
     * number of milliseconds since Jan 1, 1970
     */
    static long zonedDateTimeToMillis(ZonedDateTime zonedDateTime) {
        return zonedDateTime.toInstant().toEpochMilli();
    }

    /**
     * @param number Number instance to convert to char.
     * @return char that best represents the Number.  The result will always be a value between
     * 0 and Character.MAX_VALUE.
     */
    private static char numberToCharacter(Number number) {
        long value = number.longValue();
        if (value >= 0 && value <= Character.MAX_VALUE) {
            return (char)value;
        }
        throw new IllegalArgumentException("Value: " + value + " out of range to be converted to character.");
    }

    /**
     * Given a primitive class, return the Wrapper class equivalent.
     */
    public static Class<?> toPrimitiveWrapperClass(Class<?> primitiveClass)
    {
        if (primitiveClass == int.class) {
            return Integer.class;
        } else if (primitiveClass == long.class) {
            return Long.class;
        } else if (primitiveClass == double.class) {
            return Double.class;
        } else if (primitiveClass == float.class) {
            return Float.class;
        } else if (primitiveClass == boolean.class) {
            return Boolean.class;
        } else if (primitiveClass == char.class) {
            return Character.class;
        } else if (primitiveClass == byte.class) {
            return Byte.class;
        } else if (primitiveClass == short.class) {
            return Short.class;
        } else if (primitiveClass == void.class) {
            return Void.class;
        } else {
            throw new IllegalArgumentException("Passed in class: " + primitiveClass + " is not a primitive class");
        }
    }
}