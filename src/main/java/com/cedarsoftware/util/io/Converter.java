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
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *         limitations under the License.*
 */

public final class Converter {
    private static final String NOPE = "~nope!";
    private static final String VALUE1 = "_v";
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
    private static final Pattern extraQuotes = Pattern.compile("^\"*(.*?)\"*$");

    private static final Map<Class<?>, Convert<?>> converters = new HashMap<>();
    private static final Map<Class<?>, Map<Class<?>, Convert<?>>> supportedTypes = new HashMap<>();
    private static final Map<Class<?>, Object> fromNull = new HashMap<>();
    
    protected interface Convert<T> {
        T convert(Object fromInstance);
    }

    static {
        fromNull.put(byte.class, (byte)0);
        fromNull.put(short.class, (short)0);
        fromNull.put(int.class, 0);
        fromNull.put(long.class, 0L);
        fromNull.put(float.class, 0.0f);
        fromNull.put(double.class, 0.0d);
        fromNull.put(boolean.class, false);
        fromNull.put(char.class, (char)0);

        fromNull.put(Byte.class, null);
        fromNull.put(Short.class, null);
        fromNull.put(Integer.class, null);
        fromNull.put(Long.class, null);
        fromNull.put(Float.class, null);
        fromNull.put(Double.class, null);
        fromNull.put(Boolean.class, null);
        fromNull.put(Character.class, null);

        fromNull.put(String.class, null);
        fromNull.put(Class.class, null);
        fromNull.put(BigInteger.class, null);
        fromNull.put(BigDecimal.class, null);
        fromNull.put(AtomicInteger.class, null);
        fromNull.put(AtomicLong.class, null);
        fromNull.put(AtomicBoolean.class, null);
        fromNull.put(Date.class, null);
        fromNull.put(java.sql.Date.class, null);
        fromNull.put(Timestamp.class, null);
        fromNull.put(Calendar.class, null);
        fromNull.put(GregorianCalendar.class, null);
        fromNull.put(LocalDate.class, null);
        fromNull.put(LocalDateTime.class, null);
        fromNull.put(ZonedDateTime.class, null);
        fromNull.put(UUID.class, null);

        // Convertable types
        converters.put(byte.class, Converter::convertToByte);
        converters.put(Byte.class, Converter::convertToByte);
        converters.put(short.class, Converter::convertToShort);
        converters.put(Short.class, Converter::convertToShort);
        converters.put(int.class, Converter::convertToInteger);
        converters.put(Integer.class, Converter::convertToInteger);
        converters.put(long.class, Converter::convertToLong);
        converters.put(Long.class, Converter::convertToLong);
        converters.put(float.class, Converter::convertToFloat);
        converters.put(Float.class, Converter::convertToFloat);
        converters.put(double.class, Converter::convertToDouble);
        converters.put(Double.class, Converter::convertToDouble);
        converters.put(boolean.class, Converter::convertToBoolean);
        converters.put(Boolean.class, Converter::convertToBoolean);
        converters.put(char.class, Converter::convertToCharacter);
        converters.put(Character.class, Converter::convertToCharacter);

        converters.put(String.class, Converter::convertToString);
        converters.put(Class.class, Converter::convertToClass);
        converters.put(BigDecimal.class, Converter::convertToBigDecimal);
        converters.put(BigInteger.class, Converter::convertToBigInteger);
        converters.put(AtomicInteger.class, Converter::convertToAtomicInteger);
        converters.put(AtomicLong.class, Converter::convertToAtomicLong);
        converters.put(AtomicBoolean.class, Converter::convertToAtomicBoolean);
        converters.put(Date.class, Converter::convertToDate);
        converters.put(java.sql.Date.class, Converter::convertToSqlDate);
        converters.put(Timestamp.class, Converter::convertToTimestamp);
        converters.put(Calendar.class, Converter::convertToCalendar);
        converters.put(GregorianCalendar.class, Converter::convertToCalendar);
        converters.put(LocalDate.class, Converter::convertToLocalDate);
        converters.put(LocalDateTime.class, Converter::convertToLocalDateTime);
        converters.put(ZonedDateTime.class, Converter::convertToZonedDateTime);
        converters.put(UUID.class, Converter::convertToUUID);

        if (fromNull.size() != converters.size()) {
            new Throwable("Mismatch in size of 'fromNull' versus 'converters' in Converters.java").printStackTrace();
        }

        // ? to Byte/byte
        Map<Class<?>, Convert<?>> toByte = new HashMap<>();
        toByte.put(Map.class, null);
        toByte.put(Byte.class, fromInstance -> fromInstance);
        toByte.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? BYTE_ONE : BYTE_ZERO);
        toByte.put(Character.class, fromInstance -> (byte) ((char) fromInstance));
        toByte.put(Short.class, fromInstance -> ((Number) fromInstance).byteValue());
        toByte.put(Integer.class, fromInstance -> ((Number) fromInstance).byteValue());
        toByte.put(Long.class, fromInstance -> ((Number) fromInstance).byteValue());
        toByte.put(Float.class, fromInstance -> ((Number) fromInstance).byteValue());
        toByte.put(Double.class, fromInstance -> ((Number) fromInstance).byteValue());
        toByte.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? BYTE_ONE : BYTE_ZERO);
        toByte.put(AtomicInteger.class, fromInstance -> ((Number) fromInstance).byteValue());
        toByte.put(AtomicLong.class, fromInstance -> ((Number) fromInstance).byteValue());
        toByte.put(BigInteger.class, fromInstance -> ((Number) fromInstance).byteValue());
        toByte.put(BigDecimal.class, fromInstance -> ((Number) fromInstance).byteValue());
        toByte.put(String.class, fromInstance -> {
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
        supportedTypes.put(byte.class, toByte);
        supportedTypes.put(Byte.class, toByte);

        // ? to Short/short
        Map<Class<?>, Convert<?>> toShort = new HashMap<>();
        toShort.put(Map.class, null);
        toShort.put(Short.class, fromInstance -> fromInstance);
        toShort.put(Byte.class, fromInstance -> ((Number) fromInstance).shortValue());
        toShort.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? SHORT_ONE : SHORT_ZERO);
        toShort.put(Character.class, fromInstance -> (short) ((char) fromInstance));
        toShort.put(Integer.class, fromInstance ->  ((Number) fromInstance).shortValue());
        toShort.put(Long.class, fromInstance ->  ((Number) fromInstance).shortValue());
        toShort.put(Float.class, fromInstance ->  ((Number) fromInstance).shortValue());
        toShort.put(Double.class, fromInstance ->  ((Number) fromInstance).shortValue());
        toShort.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? SHORT_ONE : SHORT_ZERO);
        toShort.put(AtomicInteger.class, fromInstance -> ((Number) fromInstance).shortValue());
        toShort.put(AtomicLong.class, fromInstance -> ((Number) fromInstance).shortValue());
        toShort.put(BigInteger.class, fromInstance -> ((Number) fromInstance).shortValue());
        toShort.put(BigDecimal.class, fromInstance -> ((Number) fromInstance).shortValue());
        toShort.put(LocalDate.class, fromInstance -> ((LocalDate)fromInstance).toEpochDay());
        toShort.put(String.class, fromInstance -> {
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
        supportedTypes.put(short.class, toShort);
        supportedTypes.put(Short.class, toShort);

        // ? to Integer/int
        Map<Class<?>, Convert<?>> toInteger = new HashMap<>();
        toInteger.put(Map.class, null);
        toInteger.put(Integer.class, fromInstance -> fromInstance);
        toInteger.put(Byte.class, fromInstance -> ((Number) fromInstance).intValue());
        toInteger.put(Short.class, fromInstance -> ((Number) fromInstance).intValue());
        toInteger.put(Long.class, fromInstance -> ((Number) fromInstance).intValue());
        toInteger.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? INTEGER_ONE : INTEGER_ZERO);
        toInteger.put(Float.class, fromInstance ->  ((Number) fromInstance).intValue());
        toInteger.put(Double.class, fromInstance ->  ((Number) fromInstance).intValue());
        toInteger.put(Character.class, fromInstance -> (int) ((char) fromInstance));
        toInteger.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? INTEGER_ONE : INTEGER_ZERO);
        toInteger.put(AtomicInteger.class, fromInstance -> ((Number) fromInstance).intValue());
        toInteger.put(AtomicLong.class, fromInstance -> ((Number) fromInstance).intValue());
        toInteger.put(BigInteger.class, fromInstance -> ((Number) fromInstance).intValue());
        toInteger.put(BigDecimal.class, fromInstance -> ((Number) fromInstance).intValue());
        toInteger.put(LocalDate.class, fromInstance -> (int)((LocalDate)fromInstance).toEpochDay());
        toInteger.put(String.class, fromInstance -> {
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
        supportedTypes.put(int.class, toInteger);
        supportedTypes.put(Integer.class, toInteger);

        // ? to Long/long
        Map<Class<?>, Convert<?>> toLong = new HashMap<>();
        toLong.put(Map.class, null);
        toLong.put(Long.class, fromInstance -> fromInstance);
        toLong.put(Byte.class, fromInstance -> ((Number) fromInstance).longValue());
        toLong.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? LONG_ONE : LONG_ZERO);
        toLong.put(Character.class, fromInstance -> (long) ((char) fromInstance));
        toLong.put(Short.class, fromInstance ->  ((Number) fromInstance).longValue());
        toLong.put(Integer.class, fromInstance ->  ((Number) fromInstance).longValue());
        toLong.put(Float.class, fromInstance ->  ((Number) fromInstance).longValue());
        toLong.put(Double.class, fromInstance ->  ((Number) fromInstance).longValue());
        toLong.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? LONG_ONE : LONG_ZERO);
        toLong.put(AtomicInteger.class, fromInstance -> ((Number) fromInstance).longValue());
        toLong.put(AtomicLong.class, fromInstance -> ((Number) fromInstance).longValue());
        toLong.put(BigInteger.class, fromInstance -> ((Number) fromInstance).longValue());
        toLong.put(BigDecimal.class, fromInstance -> ((Number) fromInstance).longValue());
        toLong.put(Date.class, fromInstance -> ((Date) fromInstance).getTime());
        toLong.put(java.sql.Date.class, fromInstance -> ((Date) fromInstance).getTime());
        toLong.put(Timestamp.class, fromInstance -> ((Date) fromInstance).getTime());
        toLong.put(LocalDate.class, fromInstance -> ((LocalDate) fromInstance).toEpochDay());
        toLong.put(LocalDateTime.class, fromInstance -> localDateTimeToMillis((LocalDateTime) fromInstance));
        toLong.put(ZonedDateTime.class, fromInstance -> zonedDateTimeToMillis((ZonedDateTime) fromInstance));
        toLong.put(GregorianCalendar.class, fromInstance -> ((Calendar) fromInstance).getTime().getTime());
        toLong.put(String.class, fromInstance -> {
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
        supportedTypes.put(long.class, toLong);
        supportedTypes.put(Long.class, toLong);


        // ? to Float/float
        Map<Class<?>, Convert<?>> toFloat = new HashMap<>();
        toFloat.put(Map.class, null);
        toFloat.put(Float.class, fromInstance -> fromInstance);
        toFloat.put(Byte.class, fromInstance -> ((Number) fromInstance).floatValue());
        toFloat.put(Short.class, fromInstance -> ((Number) fromInstance).floatValue());
        toFloat.put(Integer.class, fromInstance -> ((Number) fromInstance).floatValue());
        toFloat.put(Long.class, fromInstance -> ((Number) fromInstance).floatValue());
        toFloat.put(Double.class, fromInstance ->  ((Number) fromInstance).floatValue());
        toFloat.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? FLOAT_ONE : FLOAT_ZERO);
        toFloat.put(Character.class, fromInstance -> (float) ((char) fromInstance));
        toFloat.put(LocalDate.class, fromInstance -> ((LocalDate) fromInstance).toEpochDay());
        toFloat.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? FLOAT_ONE : FLOAT_ZERO);
        toFloat.put(AtomicInteger.class, fromInstance -> ((Number) fromInstance).floatValue());
        toFloat.put(AtomicLong.class, fromInstance -> ((Number) fromInstance).floatValue());
        toFloat.put(BigInteger.class, fromInstance -> ((Number) fromInstance).floatValue());
        toFloat.put(BigDecimal.class, fromInstance -> ((Number) fromInstance).floatValue());
        toFloat.put(String.class, fromInstance -> {
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
        supportedTypes.put(float.class, toFloat);
        supportedTypes.put(Float.class, toFloat);

        // ? to Double/double
        Map<Class<?>, Convert<?>> toDouble = new HashMap<>();
        toDouble.put(Map.class, null);
        toDouble.put(Double.class, fromInstance -> fromInstance);
        toDouble.put(Byte.class, fromInstance -> ((Number) fromInstance).doubleValue());
        toDouble.put(Short.class, fromInstance -> ((Number) fromInstance).doubleValue());
        toDouble.put(Integer.class, fromInstance -> ((Number) fromInstance).doubleValue());
        toDouble.put(Long.class, fromInstance -> ((Number) fromInstance).doubleValue());
        toDouble.put(Float.class, fromInstance -> ((Number) fromInstance).doubleValue());
        toDouble.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? DOUBLE_ONE : DOUBLE_ZERO);
        toDouble.put(Character.class, fromInstance -> (double) ((char) fromInstance));
        toDouble.put(LocalDate.class, fromInstance -> (double)((LocalDate) fromInstance).toEpochDay());
        toDouble.put(LocalDateTime.class, fromInstance ->  (double) localDateTimeToMillis((LocalDateTime) fromInstance));
        toDouble.put(ZonedDateTime.class, fromInstance -> (double) zonedDateTimeToMillis((ZonedDateTime) fromInstance));
        toDouble.put(GregorianCalendar.class, fromInstance -> (double)((Calendar) fromInstance).getTime().getTime());
        toDouble.put(Date.class, fromInstance -> (double)((Date) fromInstance).getTime());
        toDouble.put(java.sql.Date.class, fromInstance -> (double)((Date) fromInstance).getTime());
        toDouble.put(Timestamp.class, fromInstance -> (double)((Date) fromInstance).getTime());
        toDouble.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? DOUBLE_ONE : DOUBLE_ZERO);
        toDouble.put(AtomicInteger.class, fromInstance -> ((Number) fromInstance).doubleValue());
        toDouble.put(AtomicLong.class, fromInstance -> ((Number) fromInstance).doubleValue());
        toDouble.put(BigInteger.class, fromInstance -> ((Number) fromInstance).doubleValue());
        toDouble.put(BigDecimal.class, fromInstance -> ((Number) fromInstance).doubleValue());
        toDouble.put(String.class, fromInstance -> {
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
        supportedTypes.put(double.class, toDouble);
        supportedTypes.put(Double.class, toDouble);

        // ? to Boolean/boolean
        Map<Class<?>, Convert<?>> toBoolean = new HashMap<>();
        toBoolean.put(Map.class, null);
        toBoolean.put(Boolean.class, fromInstance -> fromInstance);
        toBoolean.put(Byte.class, fromInstance -> ((Number) fromInstance).byteValue() != 0);
        toBoolean.put(Short.class, fromInstance -> ((Number) fromInstance).shortValue() != 0);
        toBoolean.put(Integer.class, fromInstance -> ((Number) fromInstance).intValue() != 0);
        toBoolean.put(Long.class, fromInstance -> ((Number) fromInstance).longValue() != 0);
        toBoolean.put(Float.class, fromInstance -> ((Number) fromInstance).floatValue() != 0.0f);
        toBoolean.put(Double.class, fromInstance -> ((Number) fromInstance).doubleValue() != 0.0d);
        toBoolean.put(Character.class, fromInstance -> ((char) fromInstance) > 0);
        toBoolean.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get());
        toBoolean.put(AtomicInteger.class, fromInstance -> ((Number) fromInstance).intValue() != 0);
        toBoolean.put(AtomicLong.class, fromInstance -> ((Number) fromInstance).longValue() != 0);
        toBoolean.put(BigInteger.class, fromInstance -> ((Number) fromInstance).longValue() != 0);
        toBoolean.put(BigDecimal.class, fromInstance -> ((Number) fromInstance).longValue() != 0);
        toBoolean.put(String.class, fromInstance -> {
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
        supportedTypes.put(boolean.class, toBoolean);
        supportedTypes.put(Boolean.class, toBoolean);

        // ? to Character/char
        Map<Class<?>, Convert<?>> toCharacter = new HashMap<>();
        toCharacter.put(Map.class, null);
        toCharacter.put(Character.class, fromInstance -> fromInstance);
        toCharacter.put(Byte.class, fromInstance -> ((Number) fromInstance).byteValue() != 0 ? '1' : '0');
        toCharacter.put(Short.class, fromInstance -> numberToCharacter((Number)fromInstance));
        toCharacter.put(Integer.class, fromInstance -> numberToCharacter((Number)fromInstance));
        toCharacter.put(Long.class, fromInstance -> numberToCharacter((Number)fromInstance));
        toCharacter.put(Float.class, fromInstance -> numberToCharacter((Number)fromInstance));
        toCharacter.put(Double.class, fromInstance -> numberToCharacter((Number)fromInstance));
        toCharacter.put(Boolean.class, fromInstance -> ((Boolean)fromInstance) ? '1' : '0');
        toCharacter.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? '1' : '0');
        toCharacter.put(AtomicInteger.class, fromInstance -> numberToCharacter((Number)fromInstance));
        toCharacter.put(AtomicLong.class, fromInstance -> numberToCharacter((Number)fromInstance));
        toCharacter.put(BigInteger.class, fromInstance -> numberToCharacter((Number)fromInstance));
        toCharacter.put(BigDecimal.class, fromInstance -> numberToCharacter((Number)fromInstance));
        toCharacter.put(String.class, fromInstance -> {
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
        supportedTypes.put(char.class, toCharacter);
        supportedTypes.put(Character.class, toCharacter);

        // ? to Class
        Map<Class<?>, Convert<?>> toClass = new HashMap<>();
        toClass.put(Map.class, null);
        toClass.put(Class.class, fromInstance -> fromInstance);
        toClass.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            Class<?> clazz = MetaUtils.classForName(str, Converter.class.getClassLoader());
            if (clazz != null) {
                return clazz;
            }
            throw new IllegalArgumentException("Cannot convert String '" + str + "' to class.  Class not found.");
        });
        supportedTypes.put(Class.class, toClass);

        // ? to BigInteger
        Map<Class<?>, Convert<?>> toBigInteger = new HashMap<>();
        toBigInteger.put(Map.class, null);
        toBigInteger.put(BigInteger.class, fromInstance -> fromInstance);
        toBigInteger.put(Byte.class, fromInstance -> BigInteger.valueOf((byte) fromInstance));
        toBigInteger.put(Short.class, fromInstance -> BigInteger.valueOf((short)fromInstance));
        toBigInteger.put(Integer.class, fromInstance -> BigInteger.valueOf((int)fromInstance));
        toBigInteger.put(Long.class, fromInstance -> BigInteger.valueOf((long)fromInstance));
        toBigInteger.put(Float.class, fromInstance -> new BigInteger(String.format("%.0f", (float)fromInstance)));
        toBigInteger.put(Double.class, fromInstance -> new BigInteger(String.format("%.0f", (double)fromInstance)));
        toBigInteger.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? BigInteger.ONE : BigInteger.ZERO);
        toBigInteger.put(Character.class, fromInstance -> BigInteger.valueOf(((char) fromInstance)));
        toBigInteger.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? BigInteger.ONE : BigInteger.ZERO);
        toBigInteger.put(AtomicInteger.class, fromInstance -> BigInteger.valueOf(((Number) fromInstance).intValue()));
        toBigInteger.put(AtomicLong.class, fromInstance -> BigInteger.valueOf(((Number) fromInstance).longValue()));
        toBigInteger.put(Date.class, fromInstance -> BigInteger.valueOf(((Date) fromInstance).getTime()));
        toBigInteger.put(java.sql.Date.class, fromInstance -> BigInteger.valueOf(((Date) fromInstance).getTime()));
        toBigInteger.put(Timestamp.class, fromInstance -> BigInteger.valueOf(((Date) fromInstance).getTime()));
        toBigInteger.put(LocalDate.class, fromInstance -> BigInteger.valueOf(((LocalDate) fromInstance).toEpochDay()));
        toBigInteger.put(LocalDateTime.class, fromInstance -> BigInteger.valueOf(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toBigInteger.put(ZonedDateTime.class, fromInstance -> BigInteger.valueOf(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toBigInteger.put(GregorianCalendar.class, fromInstance -> BigInteger.valueOf(((Calendar)fromInstance).getTime().getTime()));
        toBigInteger.put(BigDecimal.class, fromInstance -> ((BigDecimal)fromInstance).toBigInteger());
        toBigInteger.put(UUID.class, fromInstance -> {
            UUID uuid = (UUID) fromInstance;
            BigInteger mostSignificant = BigInteger.valueOf(uuid.getMostSignificantBits());
            BigInteger leastSignificant = BigInteger.valueOf(uuid.getLeastSignificantBits());
            // Shift the most significant bits to the left and add the least significant bits
            return mostSignificant.shiftLeft(64).add(leastSignificant);
        });
        toBigInteger.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return null;
            }
            return new BigInteger(str);
        });
        supportedTypes.put(BigInteger.class, toBigInteger);

        // ? to BigDecimal
        Map<Class<?>, Convert<?>> toBigDecimal = new HashMap<>();
        toBigDecimal.put(Map.class, null);
        toBigDecimal.put(BigDecimal.class, fromInstance -> fromInstance);
        toBigDecimal.put(BigInteger.class, fromInstance -> new BigDecimal((BigInteger) fromInstance));
        toBigDecimal.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? BigDecimal.ONE : BigDecimal.ZERO);
        toBigDecimal.put(Character.class, fromInstance -> BigDecimal.valueOf(((char) fromInstance)));
        toBigDecimal.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? BigDecimal.ONE : BigDecimal.ZERO);
        toBigDecimal.put(AtomicInteger.class, fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        toBigDecimal.put(AtomicLong.class, fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        toBigDecimal.put(Byte.class, fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        toBigDecimal.put(Short.class, fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        toBigDecimal.put(Integer.class, fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        toBigDecimal.put(Long.class, fromInstance -> BigDecimal.valueOf(((Number) fromInstance).longValue()));
        toBigDecimal.put(Float.class, fromInstance -> BigDecimal.valueOf((Float) fromInstance));
        toBigDecimal.put(Double.class, fromInstance -> BigDecimal.valueOf((Double) fromInstance));
        toBigDecimal.put(Date.class, fromInstance -> BigDecimal.valueOf(((Date) fromInstance).getTime()));
        toBigDecimal.put(java.sql.Date.class, fromInstance -> BigDecimal.valueOf(((Date) fromInstance).getTime()));
        toBigDecimal.put(Timestamp.class, fromInstance -> BigDecimal.valueOf(((Date) fromInstance).getTime()));
        toBigDecimal.put(LocalDate.class, fromInstance -> BigDecimal.valueOf(((LocalDate) fromInstance).toEpochDay()));
        toBigDecimal.put(LocalDateTime.class, fromInstance -> BigDecimal.valueOf(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toBigDecimal.put(ZonedDateTime.class, fromInstance -> BigDecimal.valueOf(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toBigDecimal.put(GregorianCalendar.class, fromInstance -> BigDecimal.valueOf(((Calendar) fromInstance).getTime().getTime()));
        toBigDecimal.put(UUID.class, fromInstance -> {
            UUID uuid = (UUID) fromInstance;
            BigInteger mostSignificant = BigInteger.valueOf(uuid.getMostSignificantBits());
            BigInteger leastSignificant = BigInteger.valueOf(uuid.getLeastSignificantBits());
            // Shift the most significant bits to the left and add the least significant bits
            return new BigDecimal(mostSignificant.shiftLeft(64).add(leastSignificant));
        });
        toBigDecimal.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return null;
            }
            return new BigDecimal(str);
        });
        supportedTypes.put(BigDecimal.class, toBigDecimal);
        
        // ? to Date
        Map<Class<?>, Convert<?>> toDate = new HashMap<>();
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
        supportedTypes.put(Date.class, toDate);

        // ? to java.sql.Date
        Map<Class<?>, Convert<?>> toSqlDate = new HashMap<>();
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
        supportedTypes.put(java.sql.Date.class, toSqlDate);

        // ? to Timestamp
        Map<Class<?>, Convert<?>> toTimestamp = new HashMap<>();
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
        supportedTypes.put(Timestamp.class, toTimestamp);

        // ? to Calendar
        Map<Class<?>, Convert<?>> toCalendar = new HashMap<>();
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
        supportedTypes.put(Calendar.class, toCalendar);
        supportedTypes.put(GregorianCalendar.class, toCalendar);

        // ? to LocalDate
        Map<Class<?>, Convert<?>> toLocalDate = new HashMap<>();
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
        supportedTypes.put(LocalDate.class, toLocalDate);

        // ? to LocalDateTime
        Map<Class<?>, Convert<?>> toLocalDateTime = new HashMap<>();
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
        supportedTypes.put(LocalDateTime.class, toLocalDateTime);

        // ? to ZonedDateTime
        Map<Class<?>, Convert<?>> toZonedDateTime = new HashMap<>();
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
        supportedTypes.put(ZonedDateTime.class, toZonedDateTime);
        
        // ? to AtomicBoolean
        Map<Class<?>, Convert<?>> toAtomicBoolean = new HashMap<>();
        toAtomicBoolean.put(Map.class, null);
        toAtomicBoolean.put(AtomicBoolean.class, fromInstance -> new AtomicBoolean(((AtomicBoolean) fromInstance).get()));  // mutable, so dupe
        toAtomicBoolean.put(AtomicInteger.class, fromInstance -> new AtomicBoolean(((Number) fromInstance).intValue() != 0));
        toAtomicBoolean.put(AtomicLong.class, fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        toAtomicBoolean.put(BigInteger.class, fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        toAtomicBoolean.put(BigDecimal.class, fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        toAtomicBoolean.put(Long.class, fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        toAtomicBoolean.put(Integer.class, fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        toAtomicBoolean.put(Short.class, fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        toAtomicBoolean.put(Float.class, fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        toAtomicBoolean.put(Double.class, fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        toAtomicBoolean.put(Byte.class, fromInstance -> new AtomicBoolean(((Number) fromInstance).longValue() != 0));
        toAtomicBoolean.put(Boolean.class, fromInstance -> new AtomicBoolean((Boolean) fromInstance));
        toAtomicBoolean.put(Character.class, fromInstance -> new AtomicBoolean((char) fromInstance > 0));
        toAtomicBoolean.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return null;
            }
            return new AtomicBoolean("true".equalsIgnoreCase(str));
        });
        supportedTypes.put(AtomicBoolean.class, toAtomicBoolean);

        // ? to AtomicInteger
        Map<Class<?>, Convert<?>> toAtomicInteger = new HashMap<>();
        toAtomicInteger.put(Map.class, null);
        toAtomicInteger.put(AtomicInteger.class, fromInstance -> new AtomicInteger(((Number)fromInstance).intValue())); // mutable, so dupe
        toAtomicInteger.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? new AtomicInteger(1) : new AtomicInteger(0));
        toAtomicInteger.put(AtomicLong.class, fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        toAtomicInteger.put(BigInteger.class, fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        toAtomicInteger.put(BigDecimal.class, fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        toAtomicInteger.put(Long.class, fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        toAtomicInteger.put(Integer.class, fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        toAtomicInteger.put(Short.class, fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        toAtomicInteger.put(Float.class, fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        toAtomicInteger.put(Double.class, fromInstance -> new AtomicInteger(((Number)fromInstance).intValue()));
        toAtomicInteger.put(LocalDate.class, fromInstance -> new AtomicInteger((int)((LocalDate) fromInstance).toEpochDay()));
        toAtomicInteger.put(Boolean.class, fromInstance -> ((Boolean) fromInstance) ? new AtomicInteger(1) : new AtomicInteger(0));
        toAtomicInteger.put(Byte.class, fromInstance -> new AtomicInteger(((Number) fromInstance).intValue()));
        toAtomicInteger.put(Character.class, fromInstance -> new AtomicInteger(((char) fromInstance)));
        toAtomicInteger.put(String.class, fromInstance -> {
            String str = ((String) fromInstance).trim();
            if (str.isEmpty()) {
                return null;
            }
            return new AtomicInteger(Integer.parseInt(str));
        });
        supportedTypes.put(AtomicInteger.class, toAtomicInteger);

        // ? to AtomicLong
        Map<Class<?>, Convert<?>> toAtomicLong = new HashMap<>();
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
        supportedTypes.put(AtomicLong.class, toAtomicLong);

        // ? to UUID
        Map<Class<?>, Convert<?>> toUUID = new HashMap<>();
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
        supportedTypes.put(UUID.class, toUUID);

        // ? to String
        Map<Class<?>, Convert<?>> toStr = new HashMap<>();
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
        supportedTypes.put(String.class, toStr);
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
        if ((fromInstance == null || isEmptyMap(fromInstance)) && fromNull.containsKey(toType)) {
            return (T) fromNull.get(toType);
        }

        Convert<?> converter = converters.get(toType);
        if (converter != null) {
            try {
                Object value = converter.convert(fromInstance);
                if (value != NOPE) {
                    return (T) value;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Value [" + name(fromInstance) + "] could not be converted to a '" + getShortName(toType) + "'", e);
            }
        } else {
            throw new IllegalArgumentException("Unsupported destination type '" + getShortName(toType) + "' requested for conversion");
        }

        throw new IllegalArgumentException("Unsupported value type [" + name(fromInstance) + "], not convertable to a '" + getShortName(toType) + "'");
    }

    private static boolean isEmptyMap(Object fromInstance) {
        return fromInstance instanceof Map && ((Map)fromInstance).isEmpty();
    }

    private static String getShortName(Class<?> type) {
        return java.sql.Date.class.equals(type) ? type.getName() : type.getSimpleName();
    }

    private static Object convertToString(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(String.class).get(fromType);

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

    private static Object convertToClass(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(Class.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            return fromValueMap((Map<?, ?>) fromInstance, Class.class, null);
        }
        return NOPE;
    }
    
    private static Object convertToUUID(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(UUID.class).get(fromType);

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

    private static Object convertToBigDecimal(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(BigDecimal.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            return fromValueMap((Map<?, ?>) fromInstance, BigDecimal.class, null);
        } else if (fromInstance instanceof Number) {
            return BigDecimal.valueOf(((Number) fromInstance).longValue());
        } else if (fromInstance instanceof Calendar) {
            return BigDecimal.valueOf(((Calendar) fromInstance).getTime().getTime());
        }
        return NOPE;
    }

    private static Object convertToBigInteger(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(BigInteger.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            return fromValueMap((Map<?, ?>)fromInstance, BigInteger.class, null);
        } else if (fromInstance instanceof Number) {
            return BigInteger.valueOf(((Number) fromInstance).longValue());
        } else if (fromInstance instanceof Calendar) {
            return BigInteger.valueOf(((Calendar) fromInstance).getTime().getTime());
        }
        return NOPE;
    }

    private static Object convertToSqlDate(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(java.sql.Date.class).get(fromType);

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
        Convert<?> converter = supportedTypes.get(Timestamp.class).get(fromType);

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
        Convert<?> converter = supportedTypes.get(Date.class).get(fromType);

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
        Convert<?> converter = supportedTypes.get(LocalDate.class).get(fromType);

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
        Convert<?> converter = supportedTypes.get(LocalDateTime.class).get(fromType);

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
        Convert<?> converter = supportedTypes.get(ZonedDateTime.class).get(fromType);

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
        Convert<?> converter = supportedTypes.get(Calendar.class).get(fromType);

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

    private static Object convertToCharacter(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(char.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, char.class, null);
        } else if (fromInstance instanceof Number) {
            Number number = (Number) fromInstance;
            long value = number.longValue();
            if (value >= 0 && value <= Character.MAX_VALUE) {
                return (char)value;
            }
            throw new IllegalArgumentException("value: " + value + " out of range to be converted to character.");
        }
        return NOPE;
    }

    private static Object convertToByte(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(byte.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, byte.class, null);
        } else if (fromInstance instanceof Number) {
            return ((Number) fromInstance).byteValue();
        }
        return NOPE;
    }

    private static Object convertToShort(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(short.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, short.class, null);
        } else if (fromInstance instanceof Number) {
            return ((Number) fromInstance).shortValue();
        }
        return NOPE;
    }

    private static Object convertToInteger(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(int.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, int.class, null);
        } else if (fromInstance instanceof Number) {
            return ((Number) fromInstance).intValue();
        }
        return NOPE;
    }

    private static Object convertToLong(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(long.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, long.class, null);
        } else if (fromInstance instanceof Number) {
            return ((Number) fromInstance).longValue();
        } else if (fromInstance instanceof Calendar) {
            return ((Calendar) fromInstance).getTime().getTime();
        }
        return NOPE;
    }

    private static Object convertToFloat(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(float.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, float.class, null);
        } else if (fromInstance instanceof Number) {
            return ((Number) fromInstance).floatValue();
        }
        return NOPE;
    }

    private static Object convertToDouble(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(double.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, double.class, null);
        } else if (fromInstance instanceof Number) {
            return ((Number) fromInstance).doubleValue();
        }
        return NOPE;
    }

    private static Object convertToBoolean(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(boolean.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, boolean.class, null);
        } else if (fromInstance instanceof Number) {
            return ((Number) fromInstance).longValue() != 0;
        }
        return NOPE;
    }

    private static Object convertToAtomicBoolean(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(AtomicBoolean.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, AtomicBoolean.class, null);
        } else if (fromInstance instanceof Number) {
            return new AtomicBoolean(((Number) fromInstance).longValue() != 0);
        }
        return NOPE;
    }

    private static Object convertToAtomicInteger(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(AtomicInteger.class).get(fromType);

        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle inherited types
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return fromValueMap(map, AtomicInteger.class, null);
        } else if (fromInstance instanceof Number) {
            return new AtomicInteger(((Number) fromInstance).intValue());
        }
        return NOPE;
    }

    private static Object convertToAtomicLong(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = supportedTypes.get(AtomicLong.class).get(fromType);

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

    private static Object fromValueMap(Map<?, ?> map, Class<?> type, Set<String> set) {
        Object ret = fromMap(map, VALUE1, type);
        if (ret != NOPE) {
            return ret;
        }

        ret = fromMap(map, VALUE2, type);
        if (ret == NOPE)
        {
            if (set == null || set.isEmpty()) {
                throw new IllegalArgumentException("To convert from Map to " + getShortName(type) + ", the map must include keys: '_v' or 'value' an associated value to convert from.");
            }
            else {
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

    public static Map<String, Set<String>> getSupportedConversions() {
        Map<String, Set<String>> fromTo = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Map.Entry<Class<?>, Map<Class<?>, Convert<?>>> entry : supportedTypes.entrySet()) {
            Map<Class<?>, Convert<?>> fromMap = entry.getValue();
            for (Class<?> srcClass : fromMap.keySet()) {
                fromTo.computeIfAbsent(getShortName(entry.getKey()), k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(getShortName(srcClass));
            }
        }
        return fromTo;
    }

    /**
     * @param localDate A Java LocalDate
     * @return a long representing the localDate as the number of millis since the epoch, Jan 1, 1970
     */
    public static long localDateToMillis(LocalDate localDate) {
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * @param localDateTime A Java LocalDateTime
     * @return a long representing the localDateTime as the number of milliseconds since the
     * number of milliseconds since Jan 1, 1970
     */
    public static long localDateTimeToMillis(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * @param zonedDateTime A Java ZonedDateTime
     * @return a long representing the zonedDateTime as the number of milliseconds since the
     * number of milliseconds since Jan 1, 1970
     */
    public static long zonedDateTimeToMillis(ZonedDateTime zonedDateTime) {
        return zonedDateTime.toInstant().toEpochMilli();
    }

    /**
     * @param number Number instance to convert to char.
     * @return char that best represents the Number.  The result will always be a value between
     * 0 and Character.MAX_VALUE.
     */
    public static char numberToCharacter(Number number) {
        long value = number.longValue();
        if (value >= 0 && value <= Character.MAX_VALUE) {
            return (char)value;
        }
        throw new IllegalArgumentException("Value: " + value + " out of range to be converted to character.");
    }

    /**
     * Strip leading and trailing double quotes from the passed in String. If there are more than one
     * set of quotes, ""this is weird"" then all leading and trailing quotes will be removed, yielding
     * this is weird.  Note that: ["""this is "really" weird""] will be: [this is "really" weird].
     */
    public static String removeLeadingAndTrailingQuotes(String input) {
        Matcher m = extraQuotes.matcher(input);
        if (m.find()) {
            input = m.group(1);
        }
        return input;
    }
}