package com.cedarsoftware.util.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

import com.cedarsoftware.util.DateUtilities;

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

    private static final Map<Map.Entry<Class<?>, Class<?>>, Convert<?>> factory = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<Class<?>>> cacheParentTypes = new ConcurrentHashMap<>();
    
    public interface Convert<T> {
        T convert(Object fromInstance);
    }

    private static Map.Entry<Class<?>, Class<?>> pair(Class<?> source, Class<?> target) {
        return new AbstractMap.SimpleImmutableEntry<>(source, target);
    }

    static {
        buildFactoryConversions();
    }

    static void buildFactoryConversions() {
        factory.clear();
        
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
                throw new IllegalArgumentException("Value: " + fromInstance + " not parseable as a byte value or outside " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE);
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
                throw new IllegalArgumentException("Value: " + fromInstance + " not parseable as a short value or outside " + Short.MIN_VALUE + " to " + Short.MAX_VALUE);
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
                throw new IllegalArgumentException("Value: " + fromInstance + " not parseable as an integer value or outside " + Integer.MIN_VALUE + " to " + Integer.MAX_VALUE);
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
        factory.put(pair(Calendar.class, Long.class), fromInstance -> ((Calendar) fromInstance).getTime().getTime());
        factory.put(pair(Number.class, Long.class), fromInstance -> ((Number) fromInstance).longValue());
        factory.put(pair(Map.class, Long.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, long.class, null));
        factory.put(pair(String.class, Long.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return LONG_ZERO;
            }
            try {
                return Long.valueOf(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Value: " + fromInstance + " not parseable as a long value or outside " + Long.MIN_VALUE + " to " + Long.MAX_VALUE);
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
                throw new IllegalArgumentException("Value: " + fromInstance + " not parseable as a float value");
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
                throw new IllegalArgumentException("Value: " + fromInstance + " not parseable as a double value");
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
            try {
                return new BigInteger(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Value: " + fromInstance + " not parseable as a BigInteger value.");
            }
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
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Value: " + fromInstance + " not parseable as a BigDecimal value.");
            }
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
            try {
                return new AtomicInteger(Integer.parseInt(str));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Value: " + fromInstance + " not parseable as an AtomicInteger value or outside " + Integer.MIN_VALUE + " to " + Integer.MAX_VALUE);
            }
        });

        // AtomicLong conversions supported
        factory.put(pair(Void.class, AtomicLong.class), fromInstance -> null);
        factory.put(pair(Byte.class, AtomicLong.class), fromInstance -> new AtomicLong(((Number) fromInstance).longValue()));
        factory.put(pair(Short.class, AtomicLong.class), fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        factory.put(pair(Integer.class, AtomicLong.class), fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        factory.put(pair(Long.class, AtomicLong.class), fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        factory.put(pair(Float.class, AtomicLong.class), fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        factory.put(pair(Double.class,  AtomicLong.class), fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        factory.put(pair(Boolean.class, AtomicLong.class), fromInstance -> ((Boolean) fromInstance) ? new AtomicLong(1) : new AtomicLong(0));
        factory.put(pair(Character.class, AtomicLong.class), fromInstance -> new AtomicLong(((char) fromInstance)));
        factory.put(pair(BigInteger.class, AtomicLong.class), fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        factory.put(pair(BigDecimal.class, AtomicLong.class), fromInstance -> new AtomicLong(((Number)fromInstance).longValue()));
        factory.put(pair(AtomicBoolean.class, AtomicLong.class), fromInstance -> ((AtomicBoolean) fromInstance).get() ? new AtomicLong(1) : new AtomicLong(0));
        factory.put(pair(AtomicLong.class, AtomicLong.class), fromInstance -> new AtomicLong(((Number) fromInstance).longValue()));   // mutable, so dupe
        factory.put(pair(AtomicInteger.class, AtomicLong.class), fromInstance -> new AtomicLong(((Number) fromInstance).longValue()));
        factory.put(pair(Date.class, AtomicLong.class), fromInstance -> new AtomicLong(((Date) fromInstance).getTime()));
        factory.put(pair(java.sql.Date.class, AtomicLong.class), fromInstance -> new AtomicLong(((Date) fromInstance).getTime()));
        factory.put(pair(Timestamp.class, AtomicLong.class), fromInstance -> new AtomicLong(((Date) fromInstance).getTime()));
        factory.put(pair(LocalDate.class, AtomicLong.class), fromInstance -> new AtomicLong(((LocalDate) fromInstance).toEpochDay()));
        factory.put(pair(LocalDateTime.class, AtomicLong.class), fromInstance -> new AtomicLong(localDateTimeToMillis((LocalDateTime) fromInstance)));
        factory.put(pair(ZonedDateTime.class, AtomicLong.class), fromInstance -> new AtomicLong(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        factory.put(pair(Calendar.class, AtomicLong.class), fromInstance -> new AtomicLong(((Calendar) fromInstance).getTime().getTime()));
        factory.put(pair(Number.class, AtomicLong.class), fromInstance -> new AtomicLong(((Number) fromInstance).longValue()));
        factory.put(pair(Map.class, AtomicLong.class), fromInstance -> fromValueMap((Map<?, ?>)fromInstance, AtomicLong.class, null));
        factory.put(pair(String.class, AtomicLong.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return null;
            }
            try {
                return new AtomicLong(Long.parseLong(str));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Value: " + fromInstance + " not parseable as a AtomicLong value or outside " + Long.MIN_VALUE + " to " + Long.MAX_VALUE);
            }
        });

        // Date conversions supported
        factory.put(pair(Void.class, Date.class), fromInstance -> null);
        factory.put(pair(Long.class, Date.class), fromInstance -> new Date((long) fromInstance));
        factory.put(pair(Double.class, Date.class), fromInstance -> new Date(((Number) fromInstance).longValue()));
        factory.put(pair(BigInteger.class, Date.class), fromInstance -> new Date(((Number) fromInstance).longValue()));
        factory.put(pair(BigDecimal.class, Date.class), fromInstance -> new Date(((Number) fromInstance).longValue()));
        factory.put(pair(AtomicLong.class, Date.class), fromInstance -> new Date(((Number) fromInstance).longValue()));
        factory.put(pair(Date.class, Date.class), fromInstance -> new Date(((Date) fromInstance).getTime()));
        factory.put(pair(java.sql.Date.class, Date.class), fromInstance -> new Date(((Date) fromInstance).getTime()));
        factory.put(pair(Timestamp.class, Date.class), fromInstance -> new Date(((Date) fromInstance).getTime()));
        factory.put(pair(LocalDate.class, Date.class), fromInstance -> new Date(localDateToMillis((LocalDate) fromInstance)));
        factory.put(pair(LocalDateTime.class, Date.class), fromInstance -> new Date(localDateTimeToMillis((LocalDateTime) fromInstance)));
        factory.put(pair(ZonedDateTime.class, Date.class), fromInstance -> new Date(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        factory.put(pair(Calendar.class, Date.class), fromInstance -> ((Calendar) fromInstance).getTime());
        factory.put(pair(Number.class, Date.class), fromInstance -> ((Number)fromInstance).longValue());
        factory.put(pair(Map.class, Date.class), fromInstance -> {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("time")) {
                return convert(map.get("time"), Date.class);
            } else {
                return fromValueMap(map, Date.class, MetaUtils.setOf("time"));
            }
        });
        factory.put(pair(String.class, Date.class), fromInstance -> DateUtilities.parseDate(((String) fromInstance).trim()));

        // java.sql.Date conversion supported
        factory.put(pair(Void.class, java.sql.Date.class), fromInstance -> null);
        factory.put(pair(Long.class, java.sql.Date.class), fromInstance -> new java.sql.Date((long) fromInstance));
        factory.put(pair(Double.class, java.sql.Date.class), fromInstance -> new java.sql.Date(((Number) fromInstance).longValue()));
        factory.put(pair(BigInteger.class, java.sql.Date.class), fromInstance -> new java.sql.Date(((Number) fromInstance).longValue()));
        factory.put(pair(BigDecimal.class, java.sql.Date.class), fromInstance -> new java.sql.Date(((Number) fromInstance).longValue()));
        factory.put(pair(AtomicLong.class, java.sql.Date.class), fromInstance -> new java.sql.Date(((Number) fromInstance).longValue()));
        factory.put(pair(java.sql.Date.class, java.sql.Date.class), fromInstance -> new java.sql.Date(((java.sql.Date) fromInstance).getTime()));  // java.sql.Date is mutable
        factory.put(pair(Date.class, java.sql.Date.class), fromInstance -> new java.sql.Date(((Date) fromInstance).getTime()));
        factory.put(pair(Timestamp.class, java.sql.Date.class), fromInstance -> new java.sql.Date(((Date) fromInstance).getTime()));
        factory.put(pair(LocalDate.class, java.sql.Date.class), fromInstance -> new java.sql.Date(localDateToMillis((LocalDate) fromInstance)));
        factory.put(pair(LocalDateTime.class, java.sql.Date.class), fromInstance -> new java.sql.Date(localDateTimeToMillis((LocalDateTime) fromInstance)));
        factory.put(pair(ZonedDateTime.class, java.sql.Date.class), fromInstance -> new java.sql.Date(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        factory.put(pair(Calendar.class, java.sql.Date.class), fromInstance -> new java.sql.Date(((Calendar)fromInstance).getTime().getTime()));
        factory.put(pair(Number.class, java.sql.Date.class), fromInstance -> ((Number)fromInstance).longValue());
        factory.put(pair(Map.class, java.sql.Date.class), fromInstance -> {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("time")) {
                return convert(map.get("time"), java.sql.Date.class);
            } else {
                return fromValueMap((Map<?,?>) fromInstance, java.sql.Date.class, MetaUtils.setOf("time"));
            }
        });
        factory.put(pair(String.class, java.sql.Date.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return new java.sql.Date(date.getTime());
        });

        // Timestamp conversions supported
        factory.put(pair(Void.class, Timestamp.class), fromInstance -> null);
        factory.put(pair(Long.class, Timestamp.class), fromInstance -> new Timestamp((long) fromInstance));
        factory.put(pair(Double.class, Timestamp.class), fromInstance -> new Timestamp(((Number) fromInstance).longValue()));
        factory.put(pair(BigInteger.class, Timestamp.class), fromInstance -> new Timestamp(((Number) fromInstance).longValue()));
        factory.put(pair(BigDecimal.class, Timestamp.class), fromInstance -> new Timestamp(((Number) fromInstance).longValue()));
        factory.put(pair(AtomicLong.class, Timestamp.class), fromInstance -> new Timestamp(((Number) fromInstance).longValue()));
        factory.put(pair(Timestamp.class, Timestamp.class), fromInstance -> new Timestamp(((Timestamp)fromInstance).getTime()));
        factory.put(pair(java.sql.Date.class, Timestamp.class), fromInstance -> new Timestamp(((Date) fromInstance).getTime()));
        factory.put(pair(Date.class, Timestamp.class), fromInstance -> new Timestamp(((Date) fromInstance).getTime()));
        factory.put(pair(LocalDate.class, Timestamp.class), fromInstance -> new Timestamp(localDateToMillis((LocalDate) fromInstance)));
        factory.put(pair(LocalDateTime.class, Timestamp.class), fromInstance -> new Timestamp(localDateTimeToMillis((LocalDateTime) fromInstance)));
        factory.put(pair(ZonedDateTime.class, Timestamp.class), fromInstance -> new Timestamp(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        factory.put(pair(Calendar.class, Timestamp.class), fromInstance -> new Timestamp(((Calendar)fromInstance).getTime().getTime()));
        factory.put(pair(Number.class, Timestamp.class), fromInstance -> ((Number)fromInstance).longValue());
        factory.put(pair(Map.class, Timestamp.class), fromInstance -> {
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
        });
        factory.put(pair(String.class, Timestamp.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return new Timestamp(date.getTime());
        });

        // Calendar conversions supported
        factory.put(pair(Void.class, Calendar.class), fromInstance -> null);
        factory.put(pair(Long.class, Calendar.class), fromInstance -> initCal((Long)fromInstance));
        factory.put(pair(Double.class, Calendar.class), fromInstance -> initCal(((Number) fromInstance).longValue()));
        factory.put(pair(BigInteger.class, Calendar.class), fromInstance -> initCal(((Number) fromInstance).longValue()));
        factory.put(pair(BigDecimal.class, Calendar.class), fromInstance -> initCal(((Number) fromInstance).longValue()));
        factory.put(pair(AtomicLong.class, Calendar.class), fromInstance -> initCal(((Number) fromInstance).longValue()));
        factory.put(pair(Date.class, Calendar.class), fromInstance -> initCal(((Date) fromInstance).getTime()));
        factory.put(pair(java.sql.Date.class, Calendar.class), fromInstance -> initCal(((Date) fromInstance).getTime()));
        factory.put(pair(Timestamp.class, Calendar.class), fromInstance -> initCal(((Date) fromInstance).getTime()));
        factory.put(pair(LocalDate.class, Calendar.class), fromInstance -> initCal(localDateToMillis((LocalDate)fromInstance)));
        factory.put(pair(LocalDateTime.class, Calendar.class), fromInstance -> initCal(localDateTimeToMillis((LocalDateTime) fromInstance)));
        factory.put(pair(ZonedDateTime.class, Calendar.class), fromInstance -> initCal(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        factory.put(pair(Calendar.class, Calendar.class), fromInstance -> ((Calendar) fromInstance).clone());
        factory.put(pair(Number.class, Calendar.class), fromInstance -> initCal(((Number)fromInstance).longValue()));
        factory.put(pair(Map.class, Calendar.class), fromInstance -> {
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
        });
        factory.put(pair(String.class, Calendar.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return initCal(date.getTime());
        });

        // LocalTime conversions supported
        factory.put(pair(Void.class, LocalTime.class), fromInstance -> null);
        factory.put(pair(LocalTime.class, LocalTime.class), fromInstance -> fromInstance);
        factory.put(pair(String.class, LocalTime.class), fromInstance -> {
            String strTime = (String) fromInstance;
            try {
                return LocalTime.parse(strTime);
            } catch (Exception e) {
                return DateUtilities.parseDate(strTime).toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
            }
        });
        factory.put(pair(Map.class, LocalTime.class), fromInstance -> {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("hour") && map.containsKey("minute")) {
                int hour = convert(map.get("hour"), int.class);
                int minute = convert(map.get("minute"), int.class);
                int second = convert(map.get("second"), int.class);
                int nano = convert(map.get("nano"), int.class);
                return LocalTime.of(hour, minute, second, nano);
            } else {
                return fromValueMap(map, LocalTime.class, MetaUtils.setOf("hour", "minute", "second", "nano"));
            }
        });

        // LocalDate conversions supported
        factory.put(pair(Void.class, LocalDate.class), fromInstance -> null);
        factory.put(pair(Short.class, LocalDate.class), fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        factory.put(pair(Integer.class, LocalDate.class), fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        factory.put(pair(Long.class, LocalDate.class), fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        factory.put(pair(Float.class, LocalDate.class), fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        factory.put(pair(Double.class, LocalDate.class), fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        factory.put(pair(BigInteger.class, LocalDate.class), fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        factory.put(pair(BigDecimal.class, LocalDate.class), fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        factory.put(pair(AtomicInteger.class, LocalDate.class), fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        factory.put(pair(AtomicLong.class, LocalDate.class), fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        factory.put(pair(java.sql.Date.class, LocalDate.class), fromInstance -> ((java.sql.Date) fromInstance).toLocalDate());
        factory.put(pair(Timestamp.class, LocalDate.class), fromInstance -> ((Timestamp) fromInstance).toLocalDateTime().toLocalDate());
        factory.put(pair(Date.class, LocalDate.class), fromInstance -> ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        factory.put(pair(LocalDate.class, LocalDate.class), fromInstance -> fromInstance);
        factory.put(pair(LocalDateTime.class, LocalDate.class), fromInstance -> ((LocalDateTime) fromInstance).toLocalDate());
        factory.put(pair(ZonedDateTime.class, LocalDate.class), fromInstance -> ((ZonedDateTime) fromInstance).toLocalDate());
        factory.put(pair(Calendar.class, LocalDate.class), fromInstance -> ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        factory.put(pair(Number.class, LocalDate.class), fromInstance -> LocalDate.ofEpochDay(((Number) fromInstance).longValue()));
        factory.put(pair(Map.class, LocalDate.class), fromInstance -> {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("month") && map.containsKey("day") && map.containsKey("year")) {
                int month = convert(map.get("month"), int.class);
                int day = convert(map.get("day"), int.class);
                int year = convert(map.get("year"), int.class);
                return LocalDate.of(year, month, day);
            } else {
                return fromValueMap(map, LocalDate.class, MetaUtils.setOf("year", "month", "day"));
            }
        });
        factory.put(pair(String.class, LocalDate.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        });

        // LocalDateTime conversions supported
        factory.put(pair(Void.class, LocalDateTime.class), fromInstance -> null);
        factory.put(pair(Long.class, LocalDateTime.class), fromInstance -> Instant.ofEpochMilli((Long) fromInstance).atZone(ZoneId.systemDefault()).toLocalDateTime());
        factory.put(pair(Double.class, LocalDateTime.class), fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        factory.put(pair(BigInteger.class, LocalDateTime.class), fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        factory.put(pair(BigDecimal.class, LocalDateTime.class), fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        factory.put(pair(AtomicLong.class, LocalDateTime.class), fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        factory.put(pair(java.sql.Date.class, LocalDateTime.class), fromInstance -> ((java.sql.Date)fromInstance).toLocalDate().atStartOfDay());
        factory.put(pair(Timestamp.class, LocalDateTime.class), fromInstance -> ((Timestamp) fromInstance).toLocalDateTime());
        factory.put(pair(Date.class, LocalDateTime.class), fromInstance -> ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        factory.put(pair(LocalDateTime.class, LocalDateTime.class), fromInstance -> fromInstance);
        factory.put(pair(LocalDate.class, LocalDateTime.class), fromInstance -> ((LocalDate)fromInstance).atStartOfDay());
        factory.put(pair(ZonedDateTime.class, LocalDateTime.class), fromInstance -> ((ZonedDateTime) fromInstance).toLocalDateTime());
        factory.put(pair(Calendar.class, LocalDateTime.class), fromInstance -> ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        factory.put(pair(Number.class, LocalDateTime.class), fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        factory.put(pair(Map.class, LocalDateTime.class), fromInstance -> {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, LocalDateTime.class, null);
        });
        factory.put(pair(String.class, LocalDateTime.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        });

        // ZonedDateTime conversions supported
        factory.put(pair(Void.class, ZonedDateTime.class), fromInstance -> null);
        factory.put(pair(Long.class, ZonedDateTime.class), fromInstance -> Instant.ofEpochMilli((Long) fromInstance).atZone(ZoneId.systemDefault()));
        factory.put(pair(Double.class, ZonedDateTime.class), fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()));
        factory.put(pair(BigInteger.class, ZonedDateTime.class), fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()));
        factory.put(pair(BigDecimal.class, ZonedDateTime.class), fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()));
        factory.put(pair(AtomicLong.class, ZonedDateTime.class), fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()));
        factory.put(pair(java.sql.Date.class, ZonedDateTime.class), fromInstance -> ((java.sql.Date) fromInstance).toLocalDate().atStartOfDay(ZoneId.systemDefault()));
        factory.put(pair(Timestamp.class, ZonedDateTime.class), fromInstance -> ((Timestamp) fromInstance).toInstant().atZone(ZoneId.systemDefault()));
        factory.put(pair(Date.class, ZonedDateTime.class), fromInstance -> ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()));
        factory.put(pair(LocalDate.class, ZonedDateTime.class), fromInstance -> ((LocalDate) fromInstance).atStartOfDay(ZoneId.systemDefault()));
        factory.put(pair(LocalDateTime.class, ZonedDateTime.class), fromInstance -> ((LocalDateTime) fromInstance).atZone(ZoneId.systemDefault()));
        factory.put(pair(ZonedDateTime.class, ZonedDateTime.class), fromInstance -> fromInstance);
        factory.put(pair(Calendar.class, ZonedDateTime.class), fromInstance -> ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()));
        factory.put(pair(Number.class, ZonedDateTime.class), fromInstance -> Instant.ofEpochMilli(((Number) fromInstance).longValue()).atZone(ZoneId.systemDefault()));
        factory.put(pair(Map.class, ZonedDateTime.class), fromInstance -> {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, ZonedDateTime.class, null);
        });
        factory.put(pair(String.class, ZonedDateTime.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            Date date = DateUtilities.parseDate(str);
            if (date == null) {
                return null;
            }
            return date.toInstant().atZone(ZoneId.systemDefault());
        });

        // UUID conversions supported
        factory.put(pair(Void.class, UUID.class), fromInstance -> null);
        factory.put(pair(UUID.class, UUID.class), fromInstance -> fromInstance);
        factory.put(pair(String.class, UUID.class), fromInstance -> UUID.fromString(((String)fromInstance).trim()));
        factory.put(pair(BigInteger.class, UUID.class), fromInstance -> {
            BigInteger bigInteger = (BigInteger) fromInstance;
            BigInteger mask = BigInteger.valueOf(Long.MAX_VALUE);
            long mostSignificantBits = bigInteger.shiftRight(64).and(mask).longValue();
            long leastSignificantBits = bigInteger.and(mask).longValue();
            return new UUID(mostSignificantBits, leastSignificantBits);
        });
        factory.put(pair(BigDecimal.class, UUID.class), fromInstance -> {
            BigInteger bigInt = ((BigDecimal)fromInstance).toBigInteger();
            long mostSigBits = bigInt.shiftRight(64).longValue();
            long leastSigBits = bigInt.and(new BigInteger("FFFFFFFFFFFFFFFF", 16)).longValue();
            return new UUID(mostSigBits, leastSigBits);
        });
        factory.put(pair(Map.class, UUID.class), fromInstance -> {
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
        });

        // Class conversions supported
        factory.put(pair(Void.class, Class.class), fromInstance -> null);
        factory.put(pair(Class.class, Class.class), fromInstance -> fromInstance);
        factory.put(pair(Map.class, Class.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, AtomicLong.class, null));
        factory.put(pair(String.class, Class.class), fromInstance -> {
            String str = ((String) fromInstance).trim();
            Class<?> clazz = MetaUtils.classForName(str, Converter.class.getClassLoader());
            if (clazz != null) {
                return clazz;
            }
            throw new IllegalArgumentException("Cannot convert String '" + str + "' to class.  Class not found.");
        });

        // String conversions supported
        factory.put(pair(Void.class, String.class), fromInstance -> null);
        factory.put(pair(Byte.class, String.class), Object::toString);
        factory.put(pair(Short.class, String.class), Object::toString);
        factory.put(pair(Integer.class, String.class), Object::toString);
        factory.put(pair(Long.class, String.class), Object::toString);
        factory.put(pair(Float.class, String.class), fromInstance -> new DecimalFormat("#.####################").format((float)fromInstance));
        factory.put(pair(Double.class, String.class), fromInstance -> new DecimalFormat("#.####################").format((double)fromInstance));
        factory.put(pair(Boolean.class, String.class), Object::toString);
        factory.put(pair(Character.class, String.class), fromInstance -> "" + fromInstance);
        factory.put(pair(BigInteger.class, String.class), Object::toString);
        factory.put(pair(BigDecimal.class, String.class), fromInstance -> ((BigDecimal)fromInstance).stripTrailingZeros().toPlainString());
        factory.put(pair(AtomicBoolean.class, String.class), Object::toString);
        factory.put(pair(AtomicInteger.class, String.class), Object::toString);
        factory.put(pair(AtomicLong.class, String.class), Object::toString);
        factory.put(pair(Class.class, String.class), fromInstance -> ((Class<?>) fromInstance).getName());
        factory.put(pair(Date.class, String.class), fromInstance -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Date) fromInstance));
        });
        factory.put(pair(java.sql.Date.class, String.class), fromInstance -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Date) fromInstance));
        });
        factory.put(pair(Timestamp.class, String.class), fromInstance -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Date) fromInstance));
        });
        factory.put(pair(LocalDate.class, String.class), fromInstance -> {
            LocalDate localDate = (LocalDate) fromInstance;
            return String.format("%04d-%02d-%02d", localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        });
        factory.put(pair(LocalDateTime.class, String.class), fromInstance -> {
            LocalDateTime localDateTime = (LocalDateTime) fromInstance;
            return String.format("%04d-%02d-%02dT%02d:%02d:%02d", localDateTime.getYear(), localDateTime.getMonthValue(), localDateTime.getDayOfMonth(), localDateTime.getHour(), localDateTime.getMinute(), localDateTime.getSecond());
        });
        factory.put(pair(ZonedDateTime.class, String.class), fromInstance -> {
            ZonedDateTime zonedDateTime = (ZonedDateTime) fromInstance;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
            return zonedDateTime.format(formatter);
        });
        factory.put(pair(UUID.class, String.class), Object::toString);
        factory.put(pair(Calendar.class, String.class), fromInstance -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Calendar) fromInstance).getTime());
        });
        factory.put(pair(Number.class, String.class), Object::toString);
        factory.put(pair(Map.class, String.class), fromInstance -> fromValueMap((Map<?, ?>) fromInstance, String.class, null));
        factory.put(pair(Enum.class, String.class), fromInstance -> ((Enum<?>) fromInstance).name());
        factory.put(pair(String.class, String.class), fromInstance -> fromInstance);
        factory.put(pair(Duration.class, String.class), Object::toString);
        factory.put(pair(Instant.class, String.class), Object::toString);
        factory.put(pair(LocalTime.class, String.class), Object::toString);
        factory.put(pair(MonthDay.class, String.class), Object::toString);

        // Duration conversions supported
        factory.put(pair(Void.class, Duration.class), fromInstance -> null);
        factory.put(pair(Duration.class, Duration.class), fromInstance -> fromInstance);
        factory.put(pair(String.class, Duration.class), fromInstance -> Duration.parse((String) fromInstance));
        factory.put(pair(Map.class, Duration.class), fromInstance -> {
            Map<String, Object> map = (Map<String, Object>) fromInstance;
            if (map.containsKey("seconds")) {
                long sec = convert(map.get("seconds"), long.class);
                long nanos = convert(map.get("nanos"), long.class);
                return Duration.ofSeconds(sec, nanos);
            } else {
                return fromValueMap(map, Duration.class, MetaUtils.setOf("seconds", "nanos"));
            }
        });

        // Instant conversions supported
        factory.put(pair(Void.class, Instant.class), fromInstance -> null);
        factory.put(pair(Instant.class, Instant.class), fromInstance -> fromInstance);
        factory.put(pair(String.class, Instant.class), fromInstance -> {
            try {
                return Instant.parse((String) fromInstance);
            }
            catch (Exception e) {
                return DateUtilities.parseDate((String) fromInstance).toInstant();
            }
        });
        factory.put(pair(Map.class, Instant.class), fromInstance -> {
            Map<String, Object> map = (Map<String, Object>) fromInstance;
            if (map.containsKey("seconds")) {
                long sec = convert(map.get("seconds"), long.class);
                long nanos = convert(map.get("nanos"), long.class);
                return Instant.ofEpochSecond(sec, nanos);
            } else {
                return fromValueMap(map, Instant.class, MetaUtils.setOf("seconds", "nanos"));
            }
        });

//        java.time.OffsetDateTime = com.cedarsoftware.util.io.factory.OffsetDateTimeFactory
//        java.time.OffsetTime = com.cedarsoftware.util.io.factory.OffsetTimeFactory
//        java.time.Period = com.cedarsoftware.util.io.factory.PeriodFactory
//        java.time.Year = com.cedarsoftware.util.io.factory.YearFactory
//        java.time.YearMonth = com.cedarsoftware.util.io.factory.YearMonthFactory
//        java.time.ZoneId = com.cedarsoftware.util.io.factory.ZoneIdFactory
//        java.time.ZoneOffset = com.cedarsoftware.util.io.factory.ZoneOffsetFactory
//        java.time.ZoneRegion = com.cedarsoftware.util.io.factory.ZoneIdFactory

        // MonthDay conversions supported
        factory.put(pair(Void.class, MonthDay.class), fromInstance -> null);
        factory.put(pair(MonthDay.class, MonthDay.class), fromInstance -> fromInstance);
        factory.put(pair(String.class, MonthDay.class), fromInstance -> {
            String monthDay = (String) fromInstance;
            return MonthDay.parse(monthDay);
        });
        factory.put(pair(Map.class, MonthDay.class), fromInstance -> {
            Map<String, Object> map = (Map<String, Object>) fromInstance;
            if (map.containsKey("month")) {
                int month = convert(map.get("month"), int.class);
                int day = convert(map.get("day"), int.class);
                return MonthDay.of(month, day);
            } else {
                return fromValueMap(map, MonthDay.class, MetaUtils.setOf("month", "day"));
            }
        });

        // Map conversions supported
        factory.put(pair(Void.class, Map.class), fromInstance -> null);
        factory.put(pair(Byte.class, Map.class), Converter::initMap);
        factory.put(pair(Short.class, Map.class), Converter::initMap);
        factory.put(pair(Integer.class, Map.class), Converter::initMap);
        factory.put(pair(Long.class, Map.class), Converter::initMap);
        factory.put(pair(Float.class, Map.class), Converter::initMap);
        factory.put(pair(Double.class, Map.class), Converter::initMap);
        factory.put(pair(Boolean.class, Map.class), Converter::initMap);
        factory.put(pair(Character.class, Map.class), Converter::initMap);
        factory.put(pair(BigInteger.class, Map.class), Converter::initMap);
        factory.put(pair(BigDecimal.class, Map.class), Converter::initMap);
        factory.put(pair(AtomicBoolean.class, Map.class), Converter::initMap);
        factory.put(pair(AtomicInteger.class, Map.class), Converter::initMap);
        factory.put(pair(AtomicLong.class, Map.class), Converter::initMap);
        factory.put(pair(Date.class, Map.class), Converter::initMap);
        factory.put(pair(java.sql.Date.class, Map.class), Converter::initMap);
        factory.put(pair(Timestamp.class, Map.class), Converter::initMap);
        factory.put(pair(LocalDate.class, Map.class), Converter::initMap);
        factory.put(pair(LocalDateTime.class, Map.class), Converter::initMap);
        factory.put(pair(ZonedDateTime.class, Map.class), Converter::initMap);
        factory.put(pair(Duration.class, Map.class), fromInstance -> {
            long sec = ((Duration)fromInstance).getSeconds();
            long nanos = ((Duration)fromInstance).getNano();
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("seconds", sec);
            target.put("nanos", nanos);
            return target;
        });
        factory.put(pair(Instant.class, Map.class), fromInstance -> {
            long sec = ((Instant)fromInstance).getEpochSecond();
            long nanos = ((Instant)fromInstance).getNano();
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("seconds", sec);
            target.put("nanos", nanos);
            return target;
        });
        factory.put(pair(LocalTime.class, Map.class), fromInstance -> {
            LocalTime localTime = (LocalTime)fromInstance;
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("hour", localTime.getHour());
            target.put("minute", localTime.getMinute());
            if (localTime.getNano() != 0) {  // Only output 'nano' when not 0 (and then 'second' is required).
                target.put("nano", localTime.getNano());
                target.put("second", localTime.getSecond());
            } else {    // 0 nano, 'second' is optional if 0
                if (localTime.getSecond() != 0) {
                    target.put("second", localTime.getSecond());
                }
            }
            return target;
        });
        factory.put(pair(MonthDay.class, Map.class), fromInstance -> {
            MonthDay monthDay = (MonthDay) fromInstance;
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("day", monthDay.getDayOfMonth());
            target.put("month", monthDay.getMonthValue());
            return target;
        });
        factory.put(pair(Class.class, Map.class), Converter::initMap);
        factory.put(pair(UUID.class, Map.class), Converter::initMap);
        factory.put(pair(Calendar.class, Map.class), Converter::initMap);
        factory.put(pair(Number.class, Map.class), Converter::initMap);
        factory.put(pair(Map.class, Map.class), fromInstance -> {
            Map<?, ?> source = (Map<?, ?>) fromInstance;
            Map<?, ?> copy = new LinkedHashMap<>(source);
            return copy;
        });
        factory.put(pair(Enum.class, Map.class), Converter::initMap);
    }

    /**
     * Static utility class.
     */
    private Converter() {
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
        converter = getInheritedConverter(sourceType, toType);
        if (converter != null) {
            return (T) converter.convert(fromInstance);
        }

        throw new IllegalArgumentException("Unsupported conversion, source type [" + name(fromInstance) + "] target type '" + getShortName(toType) + "'");
    }

    /**
     * Expected that source and target classes, if primitive, have already been shifted to primitive wrapper classes.
     */
    private static <T> Convert<?> getInheritedConverter(Class<?> sourceType, Class<T> toType) {
        Set<Class<?>> sourceTypes = new TreeSet<>(getClassComparator());
        Set<Class<?>> targetTypes = new TreeSet<>(getClassComparator());

        sourceTypes.addAll(getSuperClassesAndInterfaces(sourceType));
        sourceTypes.add(sourceType);
        targetTypes.addAll(getSuperClassesAndInterfaces(toType));
        targetTypes.add(toType);

        Class<?> sourceClass = sourceType;
        Class<?> targetClass = toType;

        for (Class<?> toClass : targetTypes) {
            sourceClass = null;
            targetClass = null;

            for (Class<?> fromClass : sourceTypes) {
                if (factory.containsKey(pair(fromClass, toClass))) {
                    sourceClass = fromClass;
                    targetClass = toClass;
                    break;
                }
            }

            if (sourceClass != null && targetClass != null) {
                break;
            }
        }

        Convert<?> converter = factory.get(pair(sourceClass, targetClass));
        return converter;
    }

    private static Comparator<Class<?>> getClassComparator()
    {
        return (c1, c2) -> {
            if (c1.isInterface() == c2.isInterface()) {
                // By name
                return c1.getName().compareToIgnoreCase(c2.getName());
            }
            return c1.isInterface() ? 1 : -1;
        };
    }

    private static Set<Class<?>> getSuperClassesAndInterfaces(Class<?> clazz) {

        Set<Class<?>> parentTypes = cacheParentTypes.get(clazz);
        if (parentTypes != null) {
            return parentTypes;
        }
        parentTypes = new ConcurrentSkipListSet<>(getClassComparator());
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

    private static String getShortName(Class<?> type) {
        return java.sql.Date.class.equals(type) ? type.getName() : type.getSimpleName();
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
     * Check to see if a direct-conversion from type to another type is supported.
     * @param source Class of source type.
     * @param target Class of target type.
     * @return boolean true if the Converter converts from the source type to the destination type, false otherwise.
     */
    public static boolean isDirectConversionSupportedFor(Class<?> source, Class<?> target) {
        source = toPrimitiveWrapperClass(source);
        target = toPrimitiveWrapperClass(target);
        return factory.containsKey(pair(source, target));
    }

    /**
     * Check to see if a conversion from type to another type is supported (may use inheritance via super classes/interfaces).
     * @param source Class of source type.
     * @param target Class of target type.
     * @return boolean true if the Converter converts from the source type to the destination type, false otherwise.
     */
    public static boolean isConversionSupportedFor(Class<?> source, Class<?> target) {
        source = toPrimitiveWrapperClass(source);
        target = toPrimitiveWrapperClass(target);
        if (factory.containsKey(pair(source, target))) {
            return true;
        }
        return getInheritedConverter(source, target) != null;
    }

    /**
     * @return Map<Class, Set<Class>> which contains all supported conversions. The key of the Map is a source class,
     * and the Set contains all the target types (classes) that the source can be converted to.
     */
    public static Map<Class<?>, Set<Class<?>>> allSupportedConversions() {
        Map<Class<?>, Set<Class<?>>> toFrom = new TreeMap<>((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));

        for (Map.Entry<Class<?>, Class<?>> pairs : factory.keySet()) {
            toFrom.computeIfAbsent(pairs.getKey(), k -> new TreeSet<>((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()))).add(pairs.getValue());
        }
        return toFrom;
    }

    /**
     * @return Map<String, Set<String>> which contains all supported conversions. The key of the Map is a source class
     * name, and the Set contains all the target class names that the source can be converted to.
     */
    public static Map<String, Set<String>> getSupportedConversions() {
        Map<String, Set<String>> toFrom = new TreeMap<>(String::compareToIgnoreCase);

        for (Map.Entry<Class<?>, Class<?>> pairs : factory.keySet()) {
            toFrom.computeIfAbsent(getShortName(pairs.getKey()), k -> new TreeSet<>(String::compareToIgnoreCase)).add(getShortName(pairs.getValue()));
        }
        return toFrom;
    }

    /**
     * Add a new conversion.
     * @param source Class to convert from.
     * @param target Class to convert to.
     * @param conversionFunction Convert function that converts from the source type to the destination type.
     * @return prior conversion function if one existed.
     */
    public static Convert<?> addConversion(Class<?> source, Class<?> target, Convert<?> conversionFunction) {
        source = toPrimitiveWrapperClass(source);
        target = toPrimitiveWrapperClass(target);
        return factory.put(pair(source, target), conversionFunction);
    }

    static long localDateToMillis(LocalDate localDate) {
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static long localDateTimeToMillis(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static long zonedDateTimeToMillis(ZonedDateTime zonedDateTime) {
        return zonedDateTime.toInstant().toEpochMilli();
    }

    /**
     * @param number Number instance to convert to char.
     * @return char that best represents the Number.  The result will always be a value between
     * 0 and Character.MAX_VALUE.
     * @throws IllegalArgumentException if the value exceeds the range of a char.
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
    private static Class<?> toPrimitiveWrapperClass(Class<?> primitiveClass)
    {
        if (!primitiveClass.isPrimitive()) {
            return primitiveClass;
        }
        
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