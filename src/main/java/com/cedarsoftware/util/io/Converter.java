package com.cedarsoftware.util.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
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
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handy conversion utilities.  Convert from primitive to other primitives, plus support for Number, Date,
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
    public static final String NOPE = "~nope!";
    public static final Byte BYTE_ZERO = (byte) 0;
    public static final Byte BYTE_ONE = (byte) 1;
    public static final Short SHORT_ZERO = (short) 0;
    public static final Short SHORT_ONE = (short) 1;
    public static final Integer INTEGER_ZERO = 0;
    public static final Integer INTEGER_ONE = 1;
    public static final Long LONG_ZERO = 0L;
    public static final Long LONG_ONE = 1L;
    public static final Float FLOAT_ZERO = 0.0f;
    public static final Float FLOAT_ONE = 1.0f;
    public static final Double DOUBLE_ZERO = 0.0d;
    public static final Double DOUBLE_ONE = 1.0d;
    private static final Map<Class<?>, Convert<?>> converters = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toStr = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toByte = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toShort = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toInteger = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toLong = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toFloat = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toDouble = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toBoolean = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toCharacter = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toClass = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toBigInteger = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toBigDecimal = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toAtomicBoolean = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toAtomicInteger = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toAtomicLong = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toDate = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toSqlDate = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toTimestamp = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toCalendar = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toLocalDate = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toLocalDateTime = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toZonedDateTime = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toUUID = new HashMap<>();
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
        toByte.put(Byte.class, fromInstance -> fromInstance);
        toByte.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? BYTE_ONE : BYTE_ZERO);
        toByte.put(Character.class, fromInstance -> (byte) ((char) fromInstance));
        toByte.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? BYTE_ONE : BYTE_ZERO);
        toByte.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return BYTE_ZERO;
            }
            try {
                return Byte.valueOf(((String) fromInstance).trim());
            } catch (NumberFormatException e) {
                long value = convert(fromInstance, long.class);
                if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                    throw new IllegalArgumentException("Value: " + fromInstance + " outside " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE);
                }
                return (byte) value;
            }
        });

        // ? to Short/short
        toShort.put(Short.class, fromInstance -> fromInstance);
        toShort.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? SHORT_ONE : SHORT_ZERO);
        toShort.put(Character.class, fromInstance -> (short) ((char) fromInstance));
        toShort.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? SHORT_ONE : SHORT_ZERO);
        toShort.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return SHORT_ZERO;
            }
            try {
                return Short.valueOf(((String) fromInstance).trim());
            } catch (NumberFormatException e) {
                long value = convert(fromInstance, long.class);
                if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                    throw new NumberFormatException("Value: " + fromInstance + " outside " + Short.MIN_VALUE + " to " + Short.MAX_VALUE);
                }
                return (short) value;
            }
        });

        // ? to Integer/int
        toInteger.put(Integer.class, fromInstance -> fromInstance);
        toInteger.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? INTEGER_ONE : INTEGER_ZERO);
        toInteger.put(Character.class, fromInstance -> (int) ((char) fromInstance));
        toInteger.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? INTEGER_ONE : INTEGER_ZERO);
        toInteger.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return INTEGER_ZERO;
            }
            try {
                return Integer.valueOf(((String) fromInstance).trim());
            } catch (NumberFormatException e) {
                long value = convert(fromInstance, long.class);
                if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                    throw new NumberFormatException("Value: " + fromInstance + " outside " + Integer.MIN_VALUE + " to " + Integer.MAX_VALUE);
                }
                return (int) value;
            }
        });

        // ? to Long/long
        toLong.put(Integer.class, fromInstance -> fromInstance);
        toLong.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? LONG_ONE : LONG_ZERO);
        toLong.put(Character.class, fromInstance -> (long) ((char) fromInstance));
        toLong.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? LONG_ONE : LONG_ZERO);
        toLong.put(Date.class, fromInstance -> ((Date) fromInstance).getTime());
        toLong.put(LocalDate.class, fromInstance -> localDateToMillis((LocalDate) fromInstance));
        toLong.put(LocalDateTime.class, fromInstance -> localDateTimeToMillis((LocalDateTime) fromInstance));
        toLong.put(ZonedDateTime.class, fromInstance -> zonedDateTimeToMillis((ZonedDateTime)fromInstance));
        toLong.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return LONG_ZERO;
            }
            try {
                return Long.valueOf(((String) fromInstance).trim());
            } catch (NumberFormatException e) {
                return convert(fromInstance, BigDecimal.class).longValue();
            }
        });

        // ? to Float/float
        toFloat.put(Float.class, fromInstance -> fromInstance);
        toFloat.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? FLOAT_ONE : FLOAT_ZERO);
        toFloat.put(Character.class, fromInstance -> (float) ((char) fromInstance));
        toFloat.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? FLOAT_ONE : FLOAT_ZERO);
        toFloat.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return FLOAT_ZERO;
            }
            try {
                return Float.valueOf(((String) fromInstance).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(e.getMessage(), e.getCause());
            }
        });

        // ? to Double/double
        toDouble.put(Double.class, fromInstance -> fromInstance);
        toDouble.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? DOUBLE_ONE : DOUBLE_ZERO);
        toDouble.put(Character.class, fromInstance -> (double) ((char) fromInstance));
        toDouble.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? DOUBLE_ONE : DOUBLE_ZERO);
        toDouble.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return DOUBLE_ZERO;
            }
            try {
                return Double.valueOf(((String) fromInstance).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(e.getMessage(), e.getCause());
            }
        });

        // ? to Boolean/boolean
        toBoolean.put(Boolean.class, fromInstance -> fromInstance);
        toBoolean.put(Character.class, fromInstance -> ((char) fromInstance) > 0);
        toBoolean.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get());
        toBoolean.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return false;
            }
            // faster equals check "true" and "false"
            if ("true".equals(fromInstance)) {
                return true;
            } else if ("false".equals(fromInstance)) {
                return false;
            }
            return "true".equalsIgnoreCase((String) fromInstance);
        });

        // ? to Character/char
        toCharacter.put(Character.class, fromInstance -> fromInstance);
        toCharacter.put(Boolean.class, fromInstance -> ((Boolean)fromInstance) ? '1' : '0');
        toCharacter.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? '1' : '0');
        toCharacter.put(String.class, fromInstance -> {
            if ("".equals(fromInstance)) {
                return (char)0;
            }
            return (char) Integer.parseInt(((String) fromInstance).trim());
        });

        // ? to Class
        toClass.put(Class.class, fromInstance -> fromInstance);
        toClass.put(String.class, fromInstance -> {
            Class<?> clazz = MetaUtils.classForName((String) fromInstance, Converter.class.getClassLoader());
            if (clazz != null) {
                return clazz;
            }
            throw new IllegalArgumentException("Cannot convert String '" + fromInstance + "' to class.  Class not found.");
        });

        // ? to BigInteger
        toBigInteger.put(BigInteger.class, fromInstance -> fromInstance);
        toBigInteger.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? BigInteger.ONE : BigInteger.ZERO);
        toBigInteger.put(Character.class, fromInstance -> BigInteger.valueOf(((char) fromInstance)));
        toBigInteger.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? BigInteger.ONE : BigInteger.ZERO);
        toBigInteger.put(Date.class, fromInstance -> BigInteger.valueOf(((Date) fromInstance).getTime()));
        toBigInteger.put(LocalDate.class, fromInstance -> BigInteger.valueOf(localDateToMillis((LocalDate) fromInstance)));
        toBigInteger.put(LocalDateTime.class, fromInstance -> BigInteger.valueOf(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toBigInteger.put(ZonedDateTime.class, fromInstance -> BigInteger.valueOf(zonedDateTimeToMillis((ZonedDateTime)fromInstance)));
        toBigInteger.put(BigDecimal.class, fromInstance -> ((BigDecimal)fromInstance).toBigInteger());
        toBigInteger.put(UUID.class, fromInstance -> {
            UUID uuid = (UUID) fromInstance;
            BigInteger mostSignificant = BigInteger.valueOf(uuid.getMostSignificantBits());
            BigInteger leastSignificant = BigInteger.valueOf(uuid.getLeastSignificantBits());
            // Shift the most significant bits to the left and add the least significant bits
            return mostSignificant.shiftLeft(64).add(leastSignificant);
        });
        toBigInteger.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return BigInteger.ZERO;
            }
            return new BigInteger((String) fromInstance);
        });

        // ? to BigDecimal
        toBigDecimal.put(BigDecimal.class, fromInstance -> fromInstance);
        toBigDecimal.put(Boolean.class, fromInstance -> (Boolean) fromInstance ? BigDecimal.ONE : BigDecimal.ZERO);
        toBigDecimal.put(Character.class, fromInstance -> BigDecimal.valueOf(((char) fromInstance)));
        toBigDecimal.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? BigDecimal.ONE : BigDecimal.ZERO);
        toBigDecimal.put(Float.class, fromInstance -> BigDecimal.valueOf((Float)fromInstance));
        toBigDecimal.put(Double.class, fromInstance -> BigDecimal.valueOf((Double)fromInstance));
        toBigDecimal.put(Date.class, fromInstance -> BigDecimal.valueOf(((Date) fromInstance).getTime()));
        toBigDecimal.put(LocalDate.class, fromInstance -> BigDecimal.valueOf(localDateToMillis((LocalDate) fromInstance)));
        toBigDecimal.put(LocalDateTime.class, fromInstance -> BigDecimal.valueOf(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toBigDecimal.put(ZonedDateTime.class, fromInstance -> BigDecimal.valueOf(zonedDateTimeToMillis((ZonedDateTime)fromInstance)));
        toBigDecimal.put(BigInteger.class, fromInstance -> new BigDecimal((BigInteger)fromInstance));
        toBigDecimal.put(UUID.class, fromInstance -> {
            UUID uuid = (UUID) fromInstance;
            BigInteger mostSignificant = BigInteger.valueOf(uuid.getMostSignificantBits());
            BigInteger leastSignificant = BigInteger.valueOf(uuid.getLeastSignificantBits());
            // Shift the most significant bits to the left and add the least significant bits
            return new BigDecimal(mostSignificant.shiftLeft(64).add(leastSignificant));
        });
        toBigDecimal.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal((String) fromInstance);
        });
        
        // ? to Date
        toDate.put(Date.class, fromInstance -> new Date(((Date) fromInstance).getTime()));  // Date is mutable
        toDate.put(java.sql.Date.class, fromInstance -> new Date(((java.sql.Date) fromInstance).getTime()));
        toDate.put(Timestamp.class, fromInstance -> new Date(((Timestamp) fromInstance).getTime()));
        toDate.put(LocalDate.class, fromInstance -> new Date(localDateToMillis((LocalDate) fromInstance)));
        toDate.put(LocalDateTime.class, fromInstance -> new Date(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toDate.put(ZonedDateTime.class, fromInstance -> new Date(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toDate.put(Long.class, fromInstance -> new Date((long) fromInstance));
        toDate.put(BigInteger.class, fromInstance -> new Date(((BigInteger) fromInstance).longValue()));
        toDate.put(BigDecimal.class, fromInstance -> new Date(((BigDecimal) fromInstance).longValue()));
        toDate.put(AtomicLong.class, fromInstance -> new Date(((AtomicLong) fromInstance).get()));
        toDate.put(String.class, fromInstance -> DateUtilities.parseDate(((String) fromInstance).trim()));

        // ? to java.sql.Date
        toSqlDate.put(java.sql.Date.class, fromInstance -> new java.sql.Date(((java.sql.Date) fromInstance).getTime()));  // java.sql.Date is mutable
        toSqlDate.put(Date.class, fromInstance -> new java.sql.Date(((Date) fromInstance).getTime()));
        toSqlDate.put(Timestamp.class, fromInstance -> new java.sql.Date(((Timestamp) fromInstance).getTime()));
        toSqlDate.put(LocalDate.class, fromInstance -> new java.sql.Date(localDateToMillis((LocalDate) fromInstance)));
        toSqlDate.put(LocalDateTime.class, fromInstance -> new java.sql.Date(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toSqlDate.put(ZonedDateTime.class, fromInstance -> new java.sql.Date(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toSqlDate.put(Long.class, fromInstance -> new java.sql.Date((long) fromInstance));
        toSqlDate.put(BigInteger.class, fromInstance -> new java.sql.Date(((BigInteger) fromInstance).longValue()));
        toSqlDate.put(BigDecimal.class, fromInstance -> new java.sql.Date(((BigDecimal) fromInstance).longValue()));
        toSqlDate.put(AtomicLong.class, fromInstance -> new java.sql.Date(((AtomicLong) fromInstance).get()));
        toSqlDate.put(String.class, fromInstance -> {
            Date date = DateUtilities.parseDate(((String) fromInstance).trim());
            if (date == null) {
                return null;
            }
            return new java.sql.Date(date.getTime());
        });

        // ? to Timestamp
        toTimestamp.put(Timestamp.class, fromInstance -> new Timestamp(((Timestamp)fromInstance).getTime()));  // Timestamp is mutable
        toTimestamp.put(java.sql.Date.class, fromInstance -> new Timestamp(((java.sql.Date) fromInstance).getTime()));
        toTimestamp.put(Date.class, fromInstance -> new Timestamp(((Date) fromInstance).getTime()));
        toTimestamp.put(LocalDate.class, fromInstance -> new Timestamp(localDateToMillis((LocalDate) fromInstance)));
        toTimestamp.put(LocalDateTime.class, fromInstance -> new Timestamp(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toTimestamp.put(ZonedDateTime.class, fromInstance -> new Timestamp(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toTimestamp.put(Long.class, fromInstance -> new Timestamp((long) fromInstance));
        toTimestamp.put(BigInteger.class, fromInstance -> new Timestamp(((BigInteger) fromInstance).longValue()));
        toTimestamp.put(BigDecimal.class, fromInstance -> new Timestamp(((BigDecimal) fromInstance).longValue()));
        toTimestamp.put(AtomicLong.class, fromInstance -> new Timestamp(((AtomicLong) fromInstance).get()));
        toTimestamp.put(String.class, fromInstance -> {
            Date date = DateUtilities.parseDate(((String) fromInstance).trim());
            if (date == null) {
                return null;
            }
            return new Timestamp(date.getTime());
        });

        // ? to Calendar
        toCalendar.put(Calendar.class, fromInstance -> ((Calendar) fromInstance).clone());  // Calendar is mutable
        toCalendar.put(GregorianCalendar.class, fromInstance -> ((Calendar) fromInstance).clone());
        toCalendar.put(Date.class, fromInstance -> initCal(((Date) fromInstance).getTime()));
        toCalendar.put(java.sql.Date.class, fromInstance -> initCal(((java.sql.Date) fromInstance).getTime()));
        toCalendar.put(Timestamp.class, fromInstance -> initCal(((Timestamp) fromInstance).getTime()));
        toCalendar.put(LocalDate.class, fromInstance -> initCal(localDateToMillis((LocalDate)fromInstance)));
        toCalendar.put(LocalDateTime.class, fromInstance -> initCal(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toCalendar.put(ZonedDateTime.class, fromInstance -> initCal(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toCalendar.put(Long.class, fromInstance -> initCal((Long)fromInstance));
        toCalendar.put(BigInteger.class, fromInstance -> initCal(((BigInteger) fromInstance).longValue()));
        toCalendar.put(BigDecimal.class, fromInstance -> initCal(((BigDecimal) fromInstance).longValue()));
        toCalendar.put(AtomicLong.class, fromInstance -> initCal(((AtomicLong) fromInstance).longValue()));
        toCalendar.put(String.class, fromInstance -> {
            Date date = DateUtilities.parseDate(((String) fromInstance).trim());
            if (date == null) {
                return null;
            }
            return initCal(date.getTime());
        });

        // ? to LocalDate
        toLocalDate.put(LocalDate.class, fromInstance -> fromInstance);
        toLocalDate.put(LocalDateTime.class, fromInstance -> ((LocalDateTime) fromInstance).toLocalDate());
        toLocalDate.put(ZonedDateTime.class, fromInstance -> ((ZonedDateTime) fromInstance).toLocalDate());
        toLocalDate.put(java.sql.Date.class, fromInstance -> ((java.sql.Date) fromInstance).toLocalDate());
        toLocalDate.put(Timestamp.class, fromInstance -> ((Timestamp) fromInstance).toLocalDateTime().toLocalDate());
        toLocalDate.put(Date.class, fromInstance -> ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        toLocalDate.put(Long.class, fromInstance -> LocalDate.ofEpochDay((Long) fromInstance));
        toLocalDate.put(AtomicLong.class, fromInstance -> LocalDate.ofEpochDay(((AtomicLong) fromInstance).longValue()));
        toLocalDate.put(BigInteger.class, fromInstance -> LocalDate.ofEpochDay(((BigInteger) fromInstance).longValue()));
        toLocalDate.put(BigDecimal.class, fromInstance -> LocalDate.ofEpochDay(((BigDecimal) fromInstance).longValue()));
        toLocalDate.put(String.class, fromInstance -> {
            Date date = DateUtilities.parseDate(((String) fromInstance).trim());
            if (date == null) {
                return null;
            }
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        });

        // ? to LocalDateTime
        toLocalDateTime.put(LocalDateTime.class, fromInstance -> fromInstance);
        toLocalDateTime.put(LocalDate.class, fromInstance -> ((LocalDate)fromInstance).atStartOfDay());
        toLocalDateTime.put(ZonedDateTime.class, fromInstance -> ((ZonedDateTime) fromInstance).toLocalDateTime());
        toLocalDateTime.put(Timestamp.class, fromInstance -> ((Timestamp) fromInstance).toLocalDateTime());
        toLocalDateTime.put(Date.class, fromInstance -> ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        toLocalDateTime.put(Long.class, fromInstance -> Instant.ofEpochMilli((Long) fromInstance).atZone(ZoneId.systemDefault()).toLocalDateTime());
        toLocalDateTime.put(AtomicLong.class, fromInstance -> Instant.ofEpochMilli(((AtomicLong) fromInstance).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        toLocalDateTime.put(java.sql.Date.class, fromInstance -> ((java.sql.Date)fromInstance).toLocalDate().atStartOfDay());
        toLocalDateTime.put(BigInteger.class, fromInstance -> {
            BigInteger big = (BigInteger) fromInstance;
            return Instant.ofEpochMilli(big.longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        });
        toLocalDateTime.put(BigDecimal.class, fromInstance -> {
            BigDecimal big = (BigDecimal) fromInstance;
            return Instant.ofEpochMilli(big.longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        });
        toLocalDateTime.put(String.class, fromInstance -> {
            Date date = DateUtilities.parseDate(((String) fromInstance).trim());
            if (date == null) {
                return null;
            }
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        });

        // ? to ZonedDateTime
        toZonedDateTime.put(ZonedDateTime.class, fromInstance -> fromInstance);
        toZonedDateTime.put(LocalDateTime.class, fromInstance -> ((LocalDateTime) fromInstance).atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(LocalDate.class, fromInstance -> ((LocalDate) fromInstance).atStartOfDay(ZoneId.systemDefault()));
        toZonedDateTime.put(Timestamp.class, fromInstance -> ((Timestamp) fromInstance).toInstant().atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(Date.class, fromInstance -> ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(Long.class, fromInstance -> Instant.ofEpochMilli((Long) fromInstance).atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(AtomicLong.class, fromInstance -> Instant.ofEpochMilli(((AtomicLong) fromInstance).longValue()).atZone(ZoneId.systemDefault()));
        toZonedDateTime.put(java.sql.Date.class, fromInstance -> ((java.sql.Date) fromInstance).toLocalDate().atStartOfDay(ZoneId.systemDefault()));
        toZonedDateTime.put(BigInteger.class, fromInstance -> {
            BigInteger big = (BigInteger) fromInstance;
            return Instant.ofEpochMilli(big.longValue()).atZone(ZoneId.systemDefault());
        });
        toZonedDateTime.put(BigDecimal.class, fromInstance -> {
            BigDecimal big = (BigDecimal) fromInstance;
            return Instant.ofEpochMilli(big.longValue()).atZone(ZoneId.systemDefault());
        });
        toZonedDateTime.put(String.class, fromInstance -> {
            Date date = DateUtilities.parseDate(((String) fromInstance).trim());
            if (date == null) {
                return null;
            }
            return date.toInstant().atZone(ZoneId.systemDefault());
        });
        
        // ? to AtomicBoolean
        toAtomicBoolean.put(AtomicBoolean.class, fromInstance -> new AtomicBoolean(((AtomicBoolean) fromInstance).get()));  // mutable, so dupe
        toAtomicBoolean.put(Boolean.class, fromInstance -> new AtomicBoolean((Boolean) fromInstance));
        toAtomicBoolean.put(String.class, fromInstance -> {
            String value = (String) fromInstance;
            if (MetaUtils.isEmpty(value)) {
                return new AtomicBoolean(false);
            }
            return new AtomicBoolean("true".equalsIgnoreCase(value));
        });

        // ? to AtomicInteger
        toAtomicInteger.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? new AtomicInteger(1) : new AtomicInteger(0));
        toAtomicInteger.put(Boolean.class, fromInstance -> ((Boolean) fromInstance) ? new AtomicInteger(1) : new AtomicInteger(0));
        toAtomicInteger.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return new AtomicInteger(0);
            }
            return new AtomicInteger(Integer.parseInt(((String) fromInstance).trim()));
        });

        // ? to AtomicLong
        toAtomicLong.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? new AtomicLong(1) : new AtomicLong(0));
        toAtomicLong.put(Boolean.class, fromInstance -> ((Boolean) fromInstance) ? new AtomicLong(1) : new AtomicLong(0));
        toAtomicLong.put(Date.class, fromInstance -> new AtomicLong(((Date) fromInstance).getTime()));
        toAtomicLong.put(LocalDate.class, fromInstance -> new AtomicLong(localDateToMillis((LocalDate) fromInstance)));
        toAtomicLong.put(LocalDateTime.class, fromInstance -> new AtomicLong(localDateTimeToMillis((LocalDateTime) fromInstance)));
        toAtomicLong.put(ZonedDateTime.class, fromInstance -> new AtomicLong(zonedDateTimeToMillis((ZonedDateTime) fromInstance)));
        toAtomicLong.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return new AtomicLong(0L);
            }
            try {
                return new AtomicLong(Long.parseLong(((String) fromInstance).trim()));
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException(e.getMessage(), e.getCause());
            }
        });

        // ? to UUID
        toUUID.put(UUID.class, fromInstance -> fromInstance);
        toUUID.put(String.class, fromInstance -> UUID.fromString((String)fromInstance));
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

        // ? to String
        Convert<?> toString = Object::toString;
        Convert<?> toNoExpString = Object::toString;
        toStr.put(String.class, fromInstance -> fromInstance);
        toStr.put(Boolean.class, toString);
        toStr.put(AtomicBoolean.class, toString);
        toStr.put(Byte.class, toString);
        toStr.put(Short.class, toString);
        toStr.put(Integer.class, toString);
        toStr.put(AtomicInteger.class, toString);
        toStr.put(BigInteger.class, fromInstance -> convertToBigInteger(fromInstance).toString());
        toStr.put(Long.class, toString);
        toStr.put(AtomicLong.class, toString);
        toStr.put(Double.class, toNoExpString);
        toStr.put(BigDecimal.class, fromInstance -> {
            BigDecimal bigDecimal = (BigDecimal)fromInstance;
            return bigDecimal.stripTrailingZeros().toPlainString();
        });
        toStr.put(Float.class, toNoExpString);
        toStr.put(Class.class, fromInstance -> ((Class<?>) fromInstance).getName());
        toStr.put(UUID.class, Object::toString);
        toStr.put(Date.class, fromInstance -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Date) fromInstance).getTime());
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
     *     Map containing ["value": "75.0"]
     *     convert(map, double.class)   // Converter will extract the value associated to the "value" key and convert it.
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
        if (fromInstance == null && fromNull.containsKey(toType)) {
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

    private static String getShortName(Class<?> type)
    {
        return java.sql.Date.class.equals(type) ? type.getName() : type.getSimpleName();
    }

    private static Object convertToString(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toStr.get(fromType);

        // Handle straight Class to Class case
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Calendar) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Calendar) fromInstance).getTime());
        } else if (fromInstance instanceof Enum) {
            return ((Enum<?>) fromInstance).name();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), String.class);
        }
        return NOPE;
    }

    private static Object convertToClass(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toClass.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), Class.class);
        }
        return NOPE;
    }
    
    private static Object convertToUUID(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toUUID.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("mostSigBits") && map.containsKey("leastSigBits")) {
                long mostSigBits = convert(map.get("mostSigBits"), long.class);
                long leastSigBits = convert(map.get("leastSigBits"), long.class);
                return new UUID(mostSigBits, leastSigBits);
            } else {
                throw new IllegalArgumentException("To convert Map to UUID, the Map must contain both 'mostSigBits' and 'leastSigBits' keys");
            }
        }
        return NOPE;
    }

    private static Object convertToBigDecimal(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toBigDecimal.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return BigDecimal.valueOf(((Number) fromInstance).longValue());
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), BigDecimal.class);
        } else if (fromInstance instanceof Calendar) {
            return BigDecimal.valueOf(((Calendar) fromInstance).getTime().getTime());
        }
        return NOPE;
    }

    private static Object convertToBigInteger(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toBigInteger.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return BigInteger.valueOf(((Number) fromInstance).longValue());
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), BigInteger.class);
        } else if (fromInstance instanceof Calendar) {
            return BigInteger.valueOf(((Calendar) fromInstance).getTime().getTime());
        }
        return NOPE;
    }

    private static Object convertToSqlDate(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toSqlDate.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Calendar) {
            return new java.sql.Date(((Calendar) fromInstance).getTime().getTime());
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("time")) {
                return convert(map.get("time"), java.sql.Date.class);
            } else if (map.containsKey("value")) {
                return convert(map.get("value"), java.sql.Date.class);
            } else {
                throw new IllegalArgumentException("To convert Map to java.sql.Date, the Map must contain a 'time' or a 'value' key");
            }
        }
        return NOPE;
    }

    private static Object convertToTimestamp(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toTimestamp.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Calendar) {
            return new Timestamp(((Calendar) fromInstance).getTime().getTime());
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("time")) {
                long time = convert(map.get("time"), long.class);
                int ns = convert(map.get("nanos"), int.class);
                Timestamp timeStamp = new Timestamp(time);
                timeStamp.setNanos(ns);
                return timeStamp;
            } else if (map.containsKey("value")) {
                return convert(map.get("value"), Timestamp.class);
            } else {
                throw new IllegalArgumentException("To convert Map to Timestamp, the Map must contain a 'time' and optional 'nanos' key or a 'value' key");
            }
        }
        return NOPE;
    }
    
    private static Object convertToDate(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toDate.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Calendar) {
            return ((Calendar) fromInstance).getTime();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("time")) {
                return convert(map.get("time"), Date.class);
            } else if (map.containsKey("value")) {
                return convert(map.get("value"), Date.class);
            } else {
                throw new IllegalArgumentException("To convert Map to a Date, the Map must contain a 'time' or a 'value' key");
            }
        }
        return NOPE;
    }

    private static Object convertToLocalDate(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toLocalDate.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Calendar) {
            return ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            if (map.containsKey("month") && map.containsKey("day") && map.containsKey("year")) {
                int month = convert(map.get("month"), int.class);
                int day = convert(map.get("day"), int.class);
                int year = convert(map.get("year"), int.class);
                return LocalDate.of(year, month, day);
            } else if (map.containsKey("value")) {
                return convert(map.get("value"), LocalDate.class);
            } else {
                throw new IllegalArgumentException("To convert Map to a LocalDate, the Map must contain  'year,' 'month,' and 'day' keys or a 'value' key");
            }
        }
        return NOPE;
    }

    private static Object convertToLocalDateTime(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toLocalDateTime.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Calendar) {
            return ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), LocalDateTime.class);
        }
        return NOPE;
    }
    
    private static Object convertToZonedDateTime(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toZonedDateTime.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Calendar) {
            return ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault());
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), ZonedDateTime.class);
        }
        return NOPE;
    }

    private static Object convertToCalendar(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toCalendar.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
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
            } else if (map.containsKey("value")) {
                return convert(map.get("value"), Calendar.class);
            } else {
                throw new IllegalArgumentException("To convert Map to a Calendar, the Map must contain a 'time' and optional 'zone' key, or a 'value' key");
            }
        }
        return NOPE;
    }

    private static Object convertToCharacter(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toCharacter.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            Number number = (Number) fromInstance;
            long value = number.longValue();
            if (value >= 0 && value <= Character.MAX_VALUE) {
                return (char)value;
            }
            throw new IllegalArgumentException("value: " + value + " out of range to be converted to character.");
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), char.class);
        }
        return NOPE;
    }

    private static Object convertToByte(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toByte.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return ((Number) fromInstance).byteValue();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), byte.class);
        }
        return NOPE;
    }

    private static Object convertToShort(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toShort.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return ((Number) fromInstance).shortValue();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), short.class);
        }
        return NOPE;
    }

    private static Object convertToInteger(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toInteger.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return ((Number) fromInstance).intValue();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), int.class);
        }
        return NOPE;
    }

    private static Object convertToLong(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toLong.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return ((Number) fromInstance).longValue();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), long.class);
        } else if (fromInstance instanceof Calendar) {
            return ((Calendar) fromInstance).getTime().getTime();
        }
        return NOPE;
    }

    private static Object convertToFloat(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toFloat.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return ((Number) fromInstance).floatValue();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), float.class);
        }
        return NOPE;
    }

    private static Object convertToDouble(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toDouble.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return ((Number) fromInstance).doubleValue();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), double.class);
        }
        return NOPE;
    }

    private static Object convertToBoolean(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toBoolean.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return ((Number) fromInstance).longValue() != 0;
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), boolean.class);
        }
        return NOPE;
    }

    private static Object convertToAtomicBoolean(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toAtomicBoolean.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return new AtomicBoolean(((Number) fromInstance).longValue() != 0);
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), AtomicBoolean.class);
        }
        return NOPE;
    }

    private static Object convertToAtomicInteger(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toAtomicInteger.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return new AtomicInteger(((Number) fromInstance).intValue());
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), AtomicInteger.class);
        }
        return NOPE;
    }

    private static Object convertToAtomicLong(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toAtomicLong.get(fromType);

        // Handle the Class equals Class (double dispatch)
        if (converter != null) {
            return converter.convert(fromInstance);
        }

        // Handle "is" assignable
        if (fromInstance instanceof Number) {
            return new AtomicLong(((Number) fromInstance).longValue());
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), AtomicLong.class);
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

    private static Calendar initCal(long ms)
    {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.setTimeInMillis(ms);
        return cal;
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
}