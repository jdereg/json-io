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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.util.io.factory.DateFactory;

/**
 * Handy conversion utilities.  Convert from primitive to other primitives, plus support for Date, TimeStamp SQL Date,
 * LocalDate, LocalDateTime, ZonedDateTime, Big*, Atomic*, Class, UUID, ...
 * <p>
 * `Converter.convert2*()` methods: If `null` passed in, primitive 'logical zero' is returned.
 * Example: `Converter.convert(null, boolean.class)` returns `false`.
 * <p>
 * `Converter.convertTo*()` methods: if `null` passed in, `null` is returned.  Allows "tri-state" Boolean.
 * Example: `Converter.convert(null, Boolean.class)` returns `null`.
 * <p>
 * `Converter.convert()` converts using `convertTo*()` methods for primitive wrappers, and
 * `convert2*()` methods for primitives.
 * <p>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public final class Converter {
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
    public static final BigDecimal BIG_DECIMAL_ZERO = BigDecimal.ZERO;
    public static final BigInteger BIG_INTEGER_ZERO = BigInteger.ZERO;
    private static final Map<Class<?>, Convert<?>> converters = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toStr = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toByte = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toShort = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toInteger = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toLong = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toFloat = new HashMap<>();
    private static final Map<Class<?>, Convert<?>> toDouble = new HashMap<>();
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
        converters.put(boolean.class, Converter::convert2boolean);
        converters.put(Boolean.class, Converter::convertToBoolean);
        converters.put(char.class, Converter::convert2char);
        converters.put(Character.class, Converter::convertToCharacter);
        converters.put(BigDecimal.class, Converter::convertToBigDecimal);
        converters.put(BigInteger.class, Converter::convertToBigInteger);
        converters.put(AtomicInteger.class, Converter::convertToAtomicInteger);
        converters.put(AtomicLong.class, Converter::convertToAtomicLong);
        converters.put(AtomicBoolean.class, Converter::convertToAtomicBoolean);
        converters.put(String.class, Converter::convertToString);
        converters.put(Class.class, Converter::convertToClass);
        converters.put(Calendar.class, Converter::convertToCalendar);
        converters.put(Date.class, Converter::convertToDate);
        converters.put(LocalDate.class, Converter::convertToLocalDate);
        converters.put(LocalDateTime.class, Converter::convertToLocalDateTime);
        converters.put(ZonedDateTime.class, Converter::convertToZonedDateTime);
        converters.put(java.sql.Date.class, Converter::convertToSqlDate);
        converters.put(Timestamp.class, Converter::convertToTimestamp);
        converters.put(UUID.class, Converter::convertToUUID);

        // ? to Byte/byte
        toByte.put(byte.class, fromInstance -> fromInstance);
        toByte.put(Byte.class, fromInstance -> fromInstance);
        toByte.put(boolean.class, fromInstance -> (boolean) fromInstance ? BYTE_ONE : BYTE_ZERO);
        toByte.put(Boolean.class, fromInstance -> (boolean) fromInstance ? BYTE_ONE : BYTE_ZERO);
        toShort.put(char.class, fromInstance -> (byte) ((char) fromInstance));
        toShort.put(Character.class, fromInstance -> (byte) ((char) fromInstance));
        toByte.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? BYTE_ONE : BYTE_ZERO);
        toByte.put(Map.class, fromInstance -> {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), Byte.class);
        });
        toByte.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return BYTE_ZERO;
            }
            try {
                return Byte.valueOf(((String) fromInstance).trim());
            }
            catch (NumberFormatException e) {
                long value = convert(fromInstance, long.class);
                if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                    throw new NumberFormatException("Value: " + fromInstance + " outside " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE);
                }
                return (byte) value;
            }
        });

        // ? to Short/short
        toShort.put(short.class, fromInstance -> fromInstance);
        toShort.put(Short.class, fromInstance -> fromInstance);
        toShort.put(boolean.class, fromInstance -> (boolean) fromInstance ? SHORT_ONE : SHORT_ZERO);
        toShort.put(Boolean.class, fromInstance -> (boolean) fromInstance ? SHORT_ONE : SHORT_ZERO);
        toShort.put(char.class, fromInstance -> (short) ((char) fromInstance));
        toShort.put(Character.class, fromInstance -> (short) ((char) fromInstance));
        toShort.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? SHORT_ONE : SHORT_ZERO);
        toShort.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return SHORT_ZERO;
            }
            try {
                return Short.valueOf(((String) fromInstance).trim());
            }
            catch (NumberFormatException e) {
                long value = convert(fromInstance, long.class);
                if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                    throw new NumberFormatException("Value: " + fromInstance + " outside " + Short.MIN_VALUE + " to " + Short.MAX_VALUE);
                }
                return (short) value;
            }
        });

        // ? to Integer/int
        toInteger.put(int.class, fromInstance -> fromInstance);
        toInteger.put(Integer.class, fromInstance -> fromInstance);
        toInteger.put(boolean.class, fromInstance -> (boolean) fromInstance ? INTEGER_ONE : INTEGER_ZERO);
        toInteger.put(Boolean.class, fromInstance -> (boolean) fromInstance ? INTEGER_ONE : INTEGER_ZERO);
        toInteger.put(char.class, fromInstance -> (int) ((char) fromInstance));
        toInteger.put(Character.class, fromInstance -> (int) ((char) fromInstance));
        toInteger.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? INTEGER_ONE : INTEGER_ZERO);
        toInteger.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return INTEGER_ZERO;
            }
            try {
                return Integer.valueOf(((String) fromInstance).trim());
            }
            catch (NumberFormatException e) {
                long value = convert(fromInstance, long.class);
                if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                    throw new NumberFormatException("Value: " + fromInstance + " outside " + Integer.MIN_VALUE + " to " + Integer.MAX_VALUE);
                }
                return (int) value;
            }
        });

        // ? to Long/long
        toLong.put(int.class, fromInstance -> fromInstance);
        toLong.put(Integer.class, fromInstance -> fromInstance);
        toLong.put(boolean.class, fromInstance -> (boolean) fromInstance ? LONG_ONE : LONG_ZERO);
        toLong.put(Boolean.class, fromInstance -> (boolean) fromInstance ? LONG_ONE : LONG_ZERO);
        toLong.put(char.class, fromInstance -> (long) ((char) fromInstance));
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
            }
            catch (NumberFormatException e) {
                return convertToBigDecimal(fromInstance).longValue();
            }
        });

        // ? to Float/float
        toFloat.put(float.class, fromInstance -> fromInstance);
        toFloat.put(Float.class, fromInstance -> fromInstance);
        toFloat.put(boolean.class, fromInstance -> (boolean) fromInstance ? FLOAT_ONE : FLOAT_ZERO);
        toFloat.put(Boolean.class, fromInstance -> (boolean) fromInstance ? FLOAT_ONE : FLOAT_ZERO);
        toFloat.put(char.class, fromInstance -> (float) ((char) fromInstance));
        toFloat.put(Character.class, fromInstance -> (float) ((char) fromInstance));
        toFloat.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? FLOAT_ONE : FLOAT_ZERO);
        toFloat.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return FLOAT_ZERO;
            }
            try {
                return Float.valueOf(((String) fromInstance).trim());
            }
            catch (NumberFormatException e) {
                double value = convert(fromInstance, double.class);
                if (value < Float.MIN_VALUE || value > Float.MAX_VALUE) {
                    throw new NumberFormatException("Value: " + fromInstance + " outside " + Float.MIN_VALUE + " to " + Float.MAX_VALUE);
                }
                return (float) value;
            }
        });

        // ? to Double/double
        toDouble.put(double.class, fromInstance -> fromInstance);
        toDouble.put(Double.class, fromInstance -> fromInstance);
        toDouble.put(boolean.class, fromInstance -> (boolean) fromInstance ? DOUBLE_ONE : DOUBLE_ZERO);
        toDouble.put(Boolean.class, fromInstance -> (boolean) fromInstance ? DOUBLE_ONE : DOUBLE_ZERO);
        toDouble.put(char.class, fromInstance -> (double) ((char) fromInstance));
        toDouble.put(Character.class, fromInstance -> (double) ((char) fromInstance));
        toDouble.put(AtomicBoolean.class, fromInstance -> ((AtomicBoolean) fromInstance).get() ? DOUBLE_ONE : DOUBLE_ZERO);
        toDouble.put(String.class, fromInstance -> {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return DOUBLE_ZERO;
            }
            try {
                return Double.valueOf(((String) fromInstance).trim());
            }
            catch (NumberFormatException e) {
                return convertToBigDecimal(fromInstance).doubleValue();
            }
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
        toStr.put(BigDecimal.class, fromInstance -> convertToBigDecimal(fromInstance).stripTrailingZeros().toPlainString());
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
     * example, a String value to be passed in and have it coerced to a Long.
     * <pre>
     *     Examples:
     *     Long x = convert("35", Long.class);
     *     Date d = convert("2015/01/01", Date.class)
     *     int y = convert(45.0, int.class)
     *     String date = convert(date, String.class)
     *     String date = convert(calendar, String.class)
     *     Short t = convert(true, short.class);     // returns (short) 1 or  (short) 0
     *     Long date = convert(calendar, long.class); // get calendar's time into long
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
            throw new JsonIoException("Type cannot be null in Converter.convert(value, type)");
        }

        if (fromInstance == null && fromNull.containsKey(toType)) {
            return (T) fromNull.get(toType);
        }
        
        Convert<?> converter = converters.get(toType);
        if (converter != null) {
            return (T) converter.convert(fromInstance);
        }
        
        throw new IllegalArgumentException("Unsupported type '" + toType.getName() + "' for conversion");
    }

    private static String convertToString(Object fromInstance) {
        Class<?> fromType = fromInstance.getClass();
        Convert<?> converter = toStr.get(fromType);

        // Handle straight Class to Class case
        if (converter != null) {
            return (String) converter.convert(fromInstance);
        }

        // Handle Class isAssignable Class cases
        if (fromInstance instanceof Calendar) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return simpleDateFormat.format(((Calendar) fromInstance).getTime());
        } else if (fromInstance instanceof Enum) {
            return ((Enum<?>) fromInstance).name();
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert(map.get("value"), String.class);
        }
        return nope(fromInstance, "String");
    }

    public static Class<?> convertToClass(Object fromInstance) {
        if (fromInstance instanceof Class) {
            return (Class<?>)fromInstance;
        } else if (fromInstance instanceof String) {
            Class<?> clazz = MetaUtils.classForName((String) fromInstance, Converter.class.getClassLoader());
            if (clazz != null) {
                return clazz;
            }
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convertToClass(map.get("value"));
        }
        throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'Class'");
    }
    
    public static UUID convertToUUID(Object fromInstance) {
        try {
            if (fromInstance instanceof UUID) {
                return (UUID)fromInstance;
            } else if (fromInstance instanceof String) {
                return UUID.fromString((String)fromInstance);
            } else if (fromInstance instanceof BigInteger) {
                BigInteger bigInteger = (BigInteger) fromInstance;
                BigInteger mask = BigInteger.valueOf(Long.MAX_VALUE);
                long mostSignificantBits = bigInteger.shiftRight(64).and(mask).longValue();
                long leastSignificantBits = bigInteger.and(mask).longValue();
                return new UUID(mostSignificantBits, leastSignificantBits);
            }
            else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                if (map.containsKey("mostSigBits") && map.containsKey("leastSigBits")) {
                    long mostSigBits = convert(map.get("mostSigBits"), long.class);
                    long leastSigBits = convert(map.get("leastSigBits"), long.class);
                    return new UUID(mostSigBits, leastSigBits);
                } else {
                    throw new IllegalArgumentException("To convert Map to UUID, the Map must contain both 'mostSigBits' and 'leastSigBits' keys");
                }
            }
        } catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'UUID'", e);
        }
        nope(fromInstance, "UUID");
        return null;
    }

    /**
     * Convert from the passed in instance to a BigDecimal.  If null or "" is passed in, this method will return a
     * BigDecimal with the value of 0.  Possible inputs are String (base10 numeric values in string), BigInteger,
     * any primitive/primitive wrapper, Boolean/AtomicBoolean (returns BigDecimal of 0 or 1), Date/Calendar
     * (returns BigDecimal with the value of number of milliseconds since Jan 1, 1970), and Character (returns integer
     * value of character).
     */
    public static BigDecimal convert2BigDecimal(Object fromInstance) {
        if (fromInstance == null) {
            return BIG_DECIMAL_ZERO;
        }
        return convertToBigDecimal(fromInstance);
    }

    /**
     * Convert from the passed in instance to a BigDecimal.  If null is passed in, this method will return null.  If ""
     * is passed in, this method will return a BigDecimal with the value of 0.  Possible inputs are String (base10
     * numeric values in string), BigInteger, any primitive/primitive wrapper, Boolean/AtomicBoolean (returns
     * BigDecimal of 0 or 1), Date, Calendar, LocalDate, LocalDateTime, ZonedDateTime (returns BigDecimal with the
     * value of number of milliseconds since Jan 1, 1970), and Character (returns integer value of character).
     */
    public static BigDecimal convertToBigDecimal(Object fromInstance) {
        try {
            if (fromInstance instanceof String) {
                if (MetaUtils.isEmpty((String) fromInstance)) {
                    return BigDecimal.ZERO;
                }
                return new BigDecimal(((String) fromInstance).trim());
            } else if (fromInstance instanceof BigDecimal) {
                return (BigDecimal) fromInstance;
            } else if (fromInstance instanceof BigInteger) {
                return new BigDecimal((BigInteger) fromInstance);
            } else if (fromInstance instanceof Long) {
                return new BigDecimal((Long) fromInstance);
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert2BigDecimal(map.get("value"));
            } else if (fromInstance instanceof AtomicLong) {
                return new BigDecimal(((AtomicLong) fromInstance).get());
            } else if (fromInstance instanceof Number) {
                return new BigDecimal(String.valueOf(fromInstance));
            } else if (fromInstance instanceof Boolean) {
                return (Boolean) fromInstance ? BigDecimal.ONE : BigDecimal.ZERO;
            } else if (fromInstance instanceof AtomicBoolean) {
                return ((AtomicBoolean) fromInstance).get() ? BigDecimal.ONE : BigDecimal.ZERO;
            } else if (fromInstance instanceof Date) {
                return new BigDecimal(((Date) fromInstance).getTime());
            } else if (fromInstance instanceof LocalDate) {
                return new BigDecimal(localDateToMillis((LocalDate) fromInstance));
            } else if (fromInstance instanceof LocalDateTime) {
                return new BigDecimal(localDateTimeToMillis((LocalDateTime) fromInstance));
            } else if (fromInstance instanceof ZonedDateTime) {
                return new BigDecimal(zonedDateTimeToMillis((ZonedDateTime) fromInstance));
            } else if (fromInstance instanceof Calendar) {
                return new BigDecimal(((Calendar) fromInstance).getTime().getTime());
            } else if (fromInstance instanceof Character) {
                return new BigDecimal(((Character) fromInstance));
            }
        }
        catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'BigDecimal'", e);
        }
        nope(fromInstance, "BigDecimal");
        return null;
    }

    /**
     * Convert from the passed in instance to a BigInteger.  If null or "" is passed in, this method will return a
     * BigInteger with the value of 0.  Possible inputs are String (base10 numeric values in string), BigDecimal,
     * any primitive/primitive wrapper, Boolean/AtomicBoolean (returns BigDecimal of 0 or 1), Date/Calendar
     * (returns BigDecimal with the value of number of milliseconds since Jan 1, 1970), and Character (returns integer
     * value of character).
     */
    public static BigInteger convert2BigInteger(Object fromInstance) {
        if (fromInstance == null) {
            return BIG_INTEGER_ZERO;
        }
        return convertToBigInteger(fromInstance);
    }

    /**
     * Convert from the passed in instance to a BigInteger.  If null is passed in, this method will return null.  If ""
     * is passed in, this method will return a BigInteger with the value of 0.  Possible inputs are String (base10
     * numeric values in string), BigDecimal, any primitive/primitive wrapper, Boolean/AtomicBoolean (returns
     * BigInteger of 0 or 1), Date, Calendar, LocalDate, LocalDateTime, ZonedDateTime (returns BigInteger with the value
     * of number of milliseconds since Jan 1, 1970), and Character (returns integer value of character).
     */
    public static BigInteger convertToBigInteger(Object fromInstance) {
        try {
            if (fromInstance instanceof String) {
                if (MetaUtils.isEmpty((String) fromInstance)) {
                    return BigInteger.ZERO;
                }
                return new BigInteger(((String) fromInstance).trim());
            } else if (fromInstance instanceof BigInteger) {
                return (BigInteger) fromInstance;
            } else if (fromInstance instanceof BigDecimal) {
                return ((BigDecimal) fromInstance).toBigInteger();
            } else if (fromInstance instanceof Number) {
                return new BigInteger(Long.toString(((Number) fromInstance).longValue()));
            } else if (fromInstance instanceof UUID) {
                UUID uuid = (UUID) fromInstance;
                BigInteger mostSignificant = BigInteger.valueOf(uuid.getMostSignificantBits());
                BigInteger leastSignificant = BigInteger.valueOf(uuid.getLeastSignificantBits());
                // Shift the most significant bits to the left and add the least significant bits
                return mostSignificant.shiftLeft(64).add(leastSignificant);
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert2BigInteger(map.get("value"));
            } else if (fromInstance instanceof Boolean) {
                return (Boolean) fromInstance ? BigInteger.ONE : BigInteger.ZERO;
            } else if (fromInstance instanceof AtomicBoolean) {
                return ((AtomicBoolean) fromInstance).get() ? BigInteger.ONE : BigInteger.ZERO;
            } else if (fromInstance instanceof Date) {
                return new BigInteger(Long.toString(((Date) fromInstance).getTime()));
            } else if (fromInstance instanceof LocalDate) {
                return BigInteger.valueOf(localDateToMillis((LocalDate) fromInstance));
            } else if (fromInstance instanceof LocalDateTime) {
                return BigInteger.valueOf(localDateTimeToMillis((LocalDateTime) fromInstance));
            } else if (fromInstance instanceof ZonedDateTime) {
                return BigInteger.valueOf(zonedDateTimeToMillis((ZonedDateTime) fromInstance));
            } else if (fromInstance instanceof Calendar) {
                return new BigInteger(Long.toString(((Calendar) fromInstance).getTime().getTime()));
            } else if (fromInstance instanceof Character) {
                return new BigInteger(Long.toString(((Character) fromInstance)));
            }
        }
        catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'BigInteger'", e);
        }
        nope(fromInstance, "BigInteger");
        return null;
    }

    /**
     * Convert from the passed in instance to a java.sql.Date.  If null is passed in, this method will return null.
     * Possible inputs are TimeStamp, Date, Calendar, java.sql.Date (will return a copy), LocalDate, LocalDateTime,
     * ZonedDateTime, String (which will be parsed by DateFactory into a Date and a java.sql.Date will be created from that),
     * Long, BigInteger, BigDecimal, and AtomicLong (all of which the java.sql.Date will be created directly from
     * [number of milliseconds since Jan 1, 1970]).
     */
    public static java.sql.Date convertToSqlDate(Object fromInstance) {
        try {
            if (fromInstance instanceof java.sql.Date) {   // Return a clone of the current date time because java.sql.Date is mutable.
                return new java.sql.Date(((java.sql.Date) fromInstance).getTime());
            } else if (fromInstance instanceof Timestamp) {
                Timestamp timestamp = (Timestamp) fromInstance;
                return new java.sql.Date(timestamp.getTime());
            } else if (fromInstance instanceof Date) {   // convert from java.util.Date to java.sql.Date
                return new java.sql.Date(((Date) fromInstance).getTime());
            } else if (fromInstance instanceof String) {
                Date date = DateFactory.parseDate(((String) fromInstance).trim());
                if (date == null) {
                    return null;
                }
                return new java.sql.Date(date.getTime());
            } else if (fromInstance instanceof LocalDate) {
                return new java.sql.Date(localDateToMillis((LocalDate) fromInstance));
            } else if (fromInstance instanceof LocalDateTime) {
                return new java.sql.Date(localDateTimeToMillis((LocalDateTime) fromInstance));
            } else if (fromInstance instanceof ZonedDateTime) {
                return new java.sql.Date(zonedDateTimeToMillis((ZonedDateTime) fromInstance));
            } else if (fromInstance instanceof Calendar) {
                return new java.sql.Date(((Calendar) fromInstance).getTime().getTime());
            } else if (fromInstance instanceof Long) {
                return new java.sql.Date((Long) fromInstance);
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                if (map.containsKey("time")) {
                    return convertToSqlDate(map.get("time"));
                } else if (map.containsKey("value")) {
                    return convertToSqlDate(map.get("value"));
                } else {
                    throw new IllegalArgumentException("To convert Map to java.sql.Date, the Map must contain a 'time' or a 'value' key");
                }
            } else if (fromInstance instanceof BigInteger) {
                return new java.sql.Date(((BigInteger) fromInstance).longValue());
            } else if (fromInstance instanceof BigDecimal) {
                return new java.sql.Date(((BigDecimal) fromInstance).longValue());
            } else if (fromInstance instanceof AtomicLong) {
                return new java.sql.Date(((AtomicLong) fromInstance).get());
            }
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'java.sql.Date'", e);
        }
        nope(fromInstance, "java.sql.Date");
        return null;
    }

    /**
     * Convert from the passed in instance to a Timestamp.  If null is passed in, this method will return null.
     * Possible inputs are java.sql.Date, Date, Calendar, LocalDate, LocalDateTime, ZonedDateTime, TimeStamp
     * (will return a copy), String (which will be parsed by DateFactory into a Date and a Timestamp will created
     * from that), Long, BigInteger, BigDecimal, and AtomicLong (all of which the Timestamp will be created directly
     * from [number of milliseconds since Jan 1, 1970]).
     */
    public static Timestamp convertToTimestamp(Object fromInstance) {
        try {
            if (fromInstance instanceof java.sql.Date) {   // convert from java.sql.Date to java.util.Date
                return new Timestamp(((java.sql.Date) fromInstance).getTime());
            } else if (fromInstance instanceof Timestamp) {   // return a clone of the Timestamp because it is mutable
                return new Timestamp(((Timestamp) fromInstance).getTime());
            } else if (fromInstance instanceof Date) {
                return new Timestamp(((Date) fromInstance).getTime());
            } else if (fromInstance instanceof LocalDate) {
                return new Timestamp(localDateToMillis((LocalDate) fromInstance));
            } else if (fromInstance instanceof LocalDateTime) {
                return new Timestamp(localDateTimeToMillis((LocalDateTime) fromInstance));
            } else if (fromInstance instanceof ZonedDateTime) {
                return new Timestamp(zonedDateTimeToMillis((ZonedDateTime) fromInstance));
            } else if (fromInstance instanceof String) {
                Date date = DateFactory.parseDate(((String) fromInstance).trim());
                if (date == null) {
                    return null;
                }
                return new Timestamp(date.getTime());
            } else if (fromInstance instanceof Calendar) {
                return new Timestamp(((Calendar) fromInstance).getTime().getTime());
            } else if (fromInstance instanceof Long) {
                return new Timestamp((Long) fromInstance);
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                if (map.containsKey("time")) {
                    long time = convert(map.get("time"), long.class);
                    int ns = convert(map.get("nanos"), int.class);
                    Timestamp timeStamp = new Timestamp(time);
                    timeStamp.setNanos(ns);
                    return timeStamp;
                } else {
                    throw new IllegalArgumentException("To convert Map to Timestamp, the Map must contain a 'time' key and an optional 'nanos' key");
                }
            } else if (fromInstance instanceof BigInteger) {
                return new Timestamp(((BigInteger) fromInstance).longValue());
            } else if (fromInstance instanceof BigDecimal) {
                return new Timestamp(((BigDecimal) fromInstance).longValue());
            } else if (fromInstance instanceof AtomicLong) {
                return new Timestamp(((AtomicLong) fromInstance).get());
            }
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'Timestamp'", e);
        }
        nope(fromInstance, "Timestamp");
        return null;
    }

    /**
     * Convert from the passed in instance to a Date.  If null is passed in, this method will return null.
     * Possible inputs are java.sql.Date, Timestamp, Calendar, Date (will return a copy), String (which will be parsed
     * by DateFactory and returned as a new Date instance), Long, BigInteger, BigDecimal, and AtomicLong (all of
     * which the Date will be created directly from [number of milliseconds since Jan 1, 1970]).
     */
    public static Date convertToDate(Object fromInstance) {
        try {
            if (fromInstance instanceof String) {
                return DateFactory.parseDate(((String) fromInstance).trim());
            } else if (fromInstance instanceof java.sql.Date) {   // convert from java.sql.Date to java.util.Date
                return new Date(((java.sql.Date) fromInstance).getTime());
            } else if (fromInstance instanceof Timestamp) {
                Timestamp timestamp = (Timestamp) fromInstance;
                return new Date(timestamp.getTime());
            } else if (fromInstance instanceof Date) {   // Return a clone, not the same instance because Dates are mutable
                return new Date(((Date) fromInstance).getTime());
            } else if (fromInstance instanceof LocalDate) {
                return new Date(localDateToMillis((LocalDate) fromInstance));
            } else if (fromInstance instanceof LocalDateTime) {
                return new Date(localDateTimeToMillis((LocalDateTime) fromInstance));
            } else if (fromInstance instanceof ZonedDateTime) {
                return new Date(zonedDateTimeToMillis((ZonedDateTime) fromInstance));
            } else if (fromInstance instanceof Calendar) {
                return ((Calendar) fromInstance).getTime();
            } else if (fromInstance instanceof Long) {
                return new Date((Long) fromInstance);
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                if (map.containsKey("time")) {
                    return convertToDate(map.get("time"));
                } else if (map.containsKey("value")) {
                    return convertToDate(map.get("value"));
                } else {
                    throw new IllegalArgumentException("To convert Map to java.sql.Date, the Map must contain a 'time' or a 'value' key");
                }
            } else if (fromInstance instanceof BigInteger) {
                return new Date(((BigInteger) fromInstance).longValue());
            } else if (fromInstance instanceof BigDecimal) {
                return new Date(((BigDecimal) fromInstance).longValue());
            } else if (fromInstance instanceof AtomicLong) {
                return new Date(((AtomicLong) fromInstance).get());
            }
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'Date'", e);
        }
        nope(fromInstance, "Date");
        return null;
    }

    public static LocalDate convertToLocalDate(Object fromInstance) {
        try {
            if (fromInstance instanceof String) {
                Date date = DateFactory.parseDate(((String) fromInstance).trim());
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (fromInstance instanceof LocalDate) {   // return passed in instance (no need to copy, LocalDate is immutable)
                return (LocalDate) fromInstance;
            } else if (fromInstance instanceof LocalDateTime) {
                return ((LocalDateTime) fromInstance).toLocalDate();
            } else if (fromInstance instanceof ZonedDateTime) {
                return ((ZonedDateTime) fromInstance).toLocalDate();
            } else if (fromInstance instanceof java.sql.Date) {
                return ((java.sql.Date) fromInstance).toLocalDate();
            } else if (fromInstance instanceof Timestamp) {
                return ((Timestamp) fromInstance).toLocalDateTime().toLocalDate();
            } else if (fromInstance instanceof Date) {
                return ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (fromInstance instanceof Calendar) {
                return ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (fromInstance instanceof Long) {
                Long dateInMillis = (Long) fromInstance;
                return Instant.ofEpochMilli(dateInMillis).atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (fromInstance instanceof BigInteger) {
                BigInteger big = (BigInteger) fromInstance;
                return Instant.ofEpochMilli(big.longValue()).atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (fromInstance instanceof BigDecimal) {
                BigDecimal big = (BigDecimal) fromInstance;
                return Instant.ofEpochMilli(big.longValue()).atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (fromInstance instanceof AtomicLong) {
                AtomicLong atomicLong = (AtomicLong) fromInstance;
                return Instant.ofEpochMilli(atomicLong.longValue()).atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convertToLocalDate(map.get("value"));
            }
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'LocalDate'", e);
        }
        nope(fromInstance, "LocalDate");
        return null;
    }

    public static LocalDateTime convertToLocalDateTime(Object fromInstance) {
        try {
            if (fromInstance instanceof String) {
                Date date = DateFactory.parseDate(((String) fromInstance).trim());
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            } else if (fromInstance instanceof LocalDate) {
                return ((LocalDate) fromInstance).atStartOfDay();
            } else if (fromInstance instanceof LocalDateTime) {   // return passed in instance (no need to copy, LocalDateTime is immutable)
                return ((LocalDateTime) fromInstance);
            } else if (fromInstance instanceof ZonedDateTime) {
                return ((ZonedDateTime) fromInstance).toLocalDateTime();
            } else if (fromInstance instanceof java.sql.Date) {
                return ((java.sql.Date) fromInstance).toLocalDate().atStartOfDay();
            } else if (fromInstance instanceof Timestamp) {
                return ((Timestamp) fromInstance).toLocalDateTime();
            } else if (fromInstance instanceof Date) {
                return ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            } else if (fromInstance instanceof Calendar) {
                return ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            } else if (fromInstance instanceof Long) {
                Long dateInMillis = (Long) fromInstance;
                return Instant.ofEpochMilli(dateInMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } else if (fromInstance instanceof BigInteger) {
                BigInteger big = (BigInteger) fromInstance;
                return Instant.ofEpochMilli(big.longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } else if (fromInstance instanceof BigDecimal) {
                BigDecimal big = (BigDecimal) fromInstance;
                return Instant.ofEpochMilli(big.longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } else if (fromInstance instanceof AtomicLong) {
                AtomicLong atomicLong = (AtomicLong) fromInstance;
                return Instant.ofEpochMilli(atomicLong.longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convertToLocalDateTime(map.get("value"));
            }
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'LocalDateTime'", e);
        }
        nope(fromInstance, "LocalDateTime");
        return null;
    }

    public static ZonedDateTime convertToZonedDateTime(Object fromInstance) {
        try {
            if (fromInstance instanceof String) {
                Date date = DateFactory.parseDate(((String) fromInstance).trim());
                return date.toInstant().atZone(ZoneId.systemDefault());
            } else if (fromInstance instanceof LocalDate) {
                return ((LocalDate) fromInstance).atStartOfDay(ZoneId.systemDefault());
            } else if (fromInstance instanceof LocalDateTime) {   // return passed in instance (no need to copy, LocalDateTime is immutable)
                return ((LocalDateTime) fromInstance).atZone(ZoneId.systemDefault());
            } else if (fromInstance instanceof ZonedDateTime) {   // return passed in instance (no need to copy, ZonedDateTime is immutable)
                return ((ZonedDateTime) fromInstance);
            } else if (fromInstance instanceof java.sql.Date) {
                return ((java.sql.Date) fromInstance).toLocalDate().atStartOfDay(ZoneId.systemDefault());
            } else if (fromInstance instanceof Timestamp) {
                return ((Timestamp) fromInstance).toInstant().atZone(ZoneId.systemDefault());
            } else if (fromInstance instanceof Date) {
                return ((Date) fromInstance).toInstant().atZone(ZoneId.systemDefault());
            } else if (fromInstance instanceof Calendar) {
                return ((Calendar) fromInstance).toInstant().atZone(ZoneId.systemDefault());
            } else if (fromInstance instanceof Long) {
                Long dateInMillis = (Long) fromInstance;
                return Instant.ofEpochMilli(dateInMillis).atZone(ZoneId.systemDefault());
            } else if (fromInstance instanceof BigInteger) {
                BigInteger big = (BigInteger) fromInstance;
                return Instant.ofEpochMilli(big.longValue()).atZone(ZoneId.systemDefault());
            } else if (fromInstance instanceof BigDecimal) {
                BigDecimal big = (BigDecimal) fromInstance;
                return Instant.ofEpochMilli(big.longValue()).atZone(ZoneId.systemDefault());
            } else if (fromInstance instanceof AtomicLong) {
                AtomicLong atomicLong = (AtomicLong) fromInstance;
                return Instant.ofEpochMilli(atomicLong.longValue()).atZone(ZoneId.systemDefault());
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convertToZonedDateTime(map.get("value"));
            }
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'ZonedDateTime'", e);
        }
        nope(fromInstance, "LocalDateTime");
        return null;
    }

    /**
     * Convert from the passed in instance to a Calendar.  If null is passed in, this method will return null.
     * Possible inputs are java.sql.Date, Timestamp, Date, Calendar (will return a copy), String (which will be parsed
     * by DateFactory and returned as a new Date instance), Long, BigInteger, BigDecimal, and AtomicLong (all of
     * which the Date will be created directly from [number of milliseconds since Jan 1, 1970]).
     */
    public static Calendar convertToCalendar(Object fromInstance) {
        if (fromInstance == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(convertToDate(fromInstance));
        return calendar;
    }

    /**
     * Convert from the passed in instance to a char.  If null is passed in, (char) 0 is returned. Possible inputs
     * are String, all primitive/primitive wrappers, boolean, AtomicBoolean, (false=0, true=1), and all Atomic*s.
     */
    public static char convert2char(Object fromInstance) {
        if (fromInstance == null) {
            return 0;
        }
        return convertToCharacter(fromInstance);
    }

    /**
     * Convert from the passed in instance to a Character.  If null is passed in, null is returned. Possible inputs
     * are String, all primitive/primitive wrappers, boolean, AtomicBoolean, (false=0, true=1), and all Atomic*s.
     */
    public static Character convertToCharacter(Object fromInstance) {
        try {
            if (fromInstance instanceof String) {
                if ("".equals(fromInstance)) {
                    return 0;
                }
                return (char) Integer.parseInt(((String) fromInstance).trim());
            } else if (fromInstance instanceof Number) {
                return (char) ((Number) fromInstance).shortValue();
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert2char(map.get("value"));
            } else if (fromInstance instanceof Boolean) {
                return (boolean) fromInstance ? '1' : '0';
            } else if (fromInstance instanceof AtomicBoolean) {
                return ((AtomicBoolean) fromInstance).get() ? '1' : '0';
            } else if (fromInstance instanceof Character) {
                return (Character) fromInstance;
            }
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to a 'Character'", e);
        }
        nope(fromInstance, "Character");
        return null;
    }

    private static byte convertToByte(Object fromInstance) {
        try {
            Class<?> fromType = fromInstance.getClass();
            Convert<?> converter = toByte.get(fromType);

            // Handle the Class equals Class (double dispatch)
            if (converter != null) {
                return (Byte)converter.convert(fromInstance);
            }

            // Handle Class is assignable Class
            if (fromInstance instanceof Number) {
                return ((Number) fromInstance).byteValue();
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert(map.get("value"), byte.class);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("value [" + name(fromInstance) + "] could not be converted to a 'byte'", e);
        }
        nope(fromInstance, "byte");
        return 0;
    }

    private static short convertToShort(Object fromInstance) {
        try {
            Class<?> fromType = fromInstance.getClass();
            Convert<?> converter = toShort.get(fromType);

            // Handle the Class equals Class (double dispatch)
            if (converter != null) {
                return (Short)converter.convert(fromInstance);
            }

            // Handle Class is assignable Class
            if (fromInstance instanceof Number) {
                return ((Number) fromInstance).shortValue();
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert(map.get("value"), short.class);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("value [" + name(fromInstance) + "] could not be converted to a 'short'", e);
        }
        nope(fromInstance, "short");
        return 0;
    }

    private static Integer convertToInteger(Object fromInstance) {
        try {
            Class<?> fromType = fromInstance.getClass();
            Convert<?> converter = toInteger.get(fromType);

            // Handle the Class equals Class (double dispatch)
            if (converter != null) {
                return (Integer)converter.convert(fromInstance);
            }

            // Handle Class is assignable Class
            if (fromInstance instanceof Number) {
                return ((Number) fromInstance).intValue();
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert(map.get("value"), int.class);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("value [" + name(fromInstance) + "] could not be converted to an 'int'", e);
        }
        nope(fromInstance, "int");
        return null;
    }

    private static Long convertToLong(Object fromInstance) {
        try {
            Class<?> fromType = fromInstance.getClass();
            Convert<?> converter = toLong.get(fromType);

            // Handle the Class equals Class (double dispatch)
            if (converter != null) {
                return (Long)converter.convert(fromInstance);
            }

            // Handle Class is assignable Class
            if (fromInstance instanceof Number) {
                return ((Number) fromInstance).longValue();
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert(map.get("value"), long.class);
            } else if (fromInstance instanceof Calendar) {
                return ((Calendar) fromInstance).getTime().getTime();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("value [" + name(fromInstance) + "] could not be converted to a long'", e);
        }
        nope(fromInstance, "long");
        return null;
    }

    private static Float convertToFloat(Object fromInstance) {
        try {
            Class<?> fromType = fromInstance.getClass();
            Convert<?> converter = toFloat.get(fromType);

            // Handle the Class equals Class (double dispatch)
            if (converter != null) {
                return (Float)converter.convert(fromInstance);
            }

            // Handle Class is assignable Class
            if (fromInstance instanceof Number) {
                return ((Number) fromInstance).floatValue();
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert(map.get("value"), float.class);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("value [" + name(fromInstance) + "] could not be converted to a 'float'", e);
        }
        nope(fromInstance, "float");
        return null;
    }

    private static Double convertToDouble(Object fromInstance) {
        try {
            Class<?> fromType = fromInstance.getClass();
            Convert<?> converter = toDouble.get(fromType);

            // Handle the Class equals Class (double dispatch)
            if (converter != null) {
                return (Double)converter.convert(fromInstance);
            }

            // Handle Class is assignable Class
            if (fromInstance instanceof Number) {
                return ((Number) fromInstance).doubleValue();
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert(map.get("value"), double.class);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("value [" + name(fromInstance) + "] could not be converted to a 'double'", e);
        }
        nope(fromInstance, "double");
        return null;
    }

    /**
     * Convert from the passed in instance to a boolean.  If null is passed in, false is returned. Possible inputs
     * are String, all primitive/primitive wrappers, boolean, AtomicBoolean, (false=0, true=1), and all Atomic*s.
     */
    public static boolean convert2boolean(Object fromInstance) {
        if (fromInstance == null) {
            return false;
        }
        return convertToBoolean(fromInstance);
    }

    /**
     * Convert from the passed in instance to a Boolean.  If null is passed in, null is returned. Possible inputs
     * are String, all primitive/primitive wrappers, boolean, AtomicBoolean, (false=0, true=1), and all Atomic*s.
     */
    public static Boolean convertToBoolean(Object fromInstance) {
        if (fromInstance instanceof Boolean) {
            return (Boolean) fromInstance;
        } else if (fromInstance instanceof String) {
            // faster equals check "true" and "false"
            if ("true".equals(fromInstance)) {
                return true;
            } else if ("false".equals(fromInstance)) {
                return false;
            }
            return "true".equalsIgnoreCase((String) fromInstance);
        } else if (fromInstance instanceof Number) {
            return ((Number) fromInstance).longValue() != 0;
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert2boolean(map.get("value"));
        } else if (fromInstance instanceof AtomicBoolean) {
            return ((AtomicBoolean) fromInstance).get();
        }
        nope(fromInstance, "Boolean");
        return null;
    }

    /**
     * Convert from the passed in instance to an AtomicInteger.  If null is passed in, a new AtomicInteger(0) is
     * returned. Possible inputs are String, all primitive/primitive wrappers, boolean, AtomicBoolean,
     * (false=0, true=1), and all Atomic*s.
     */
    public static AtomicInteger convert2AtomicInteger(Object fromInstance) {
        if (fromInstance == null) {
            return new AtomicInteger(0);
        }
        return convertToAtomicInteger(fromInstance);
    }

    /**
     * Convert from the passed in instance to an AtomicInteger.  If null is passed in, null is returned. Possible inputs
     * are String, all primitive/primitive wrappers, boolean, AtomicBoolean, (false=0, true=1), and all Atomic*s.
     */
    public static AtomicInteger convertToAtomicInteger(Object fromInstance) {
        try {
            if (fromInstance instanceof AtomicInteger) {   // return a new instance because AtomicInteger is mutable
                return new AtomicInteger(((AtomicInteger) fromInstance).get());
            } else if (fromInstance instanceof String) {
                if (MetaUtils.isEmpty((String) fromInstance)) {
                    return new AtomicInteger(0);
                }
                return new AtomicInteger(Integer.parseInt(((String) fromInstance).trim()));
            } else if (fromInstance instanceof Number) {
                return new AtomicInteger(((Number) fromInstance).intValue());
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert2AtomicInteger(map.get("value"));
            } else if (fromInstance instanceof Boolean) {
                return (Boolean) fromInstance ? new AtomicInteger(1) : new AtomicInteger(0);
            } else if (fromInstance instanceof AtomicBoolean) {
                return ((AtomicBoolean) fromInstance).get() ? new AtomicInteger(1) : new AtomicInteger(0);
            }
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to an 'AtomicInteger'", e);
        }
        nope(fromInstance, "AtomicInteger");
        return null;
    }

    /**
     * Convert from the passed in instance to an AtomicLong.  If null is passed in, new AtomicLong(0L) is returned.
     * Possible inputs are String, all primitive/primitive wrappers, boolean, AtomicBoolean, (false=0, true=1), and
     * all Atomic*s.  In addition, Date, LocalDate, LocalDateTime, ZonedDateTime, java.sql.Date, Timestamp, and Calendar
     * can be passed in, in which case the AtomicLong returned is the number of milliseconds since Jan 1, 1970.
     */
    public static AtomicLong convert2AtomicLong(Object fromInstance) {
        if (fromInstance == null) {
            return new AtomicLong(0);
        }
        return convertToAtomicLong(fromInstance);
    }

    /**
     * Convert from the passed in instance to an AtomicLong.  If null is passed in, null is returned. Possible inputs
     * are String, all primitive/primitive wrappers, boolean, AtomicBoolean, (false=0, true=1), and all Atomic*s.  In
     * addition, Date, LocalDate, LocalDateTime, ZonedDateTime, java.sql.Date, Timestamp, and Calendar can be passed in,
     * in which case the AtomicLong returned is the number of milliseconds since Jan 1, 1970.
     */
    public static AtomicLong convertToAtomicLong(Object fromInstance) {
        try {
            if (fromInstance instanceof String) {
                if (MetaUtils.isEmpty((String) fromInstance)) {
                    return new AtomicLong(0);
                }
                return new AtomicLong(Long.parseLong(((String) fromInstance).trim()));
            } else if (fromInstance instanceof AtomicLong) {   // return a clone of the AtomicLong because it is mutable
                return new AtomicLong(((AtomicLong) fromInstance).get());
            } else if (fromInstance instanceof Number) {
                return new AtomicLong(((Number) fromInstance).longValue());
            } else if (fromInstance instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) fromInstance;
                return convert2AtomicLong(map.get("value"));
            } else if (fromInstance instanceof Date) {
                return new AtomicLong(((Date) fromInstance).getTime());
            } else if (fromInstance instanceof LocalDate) {
                return new AtomicLong(localDateToMillis((LocalDate) fromInstance));
            } else if (fromInstance instanceof LocalDateTime) {
                return new AtomicLong(localDateTimeToMillis((LocalDateTime) fromInstance));
            } else if (fromInstance instanceof ZonedDateTime) {
                return new AtomicLong(zonedDateTimeToMillis((ZonedDateTime) fromInstance));
            } else if (fromInstance instanceof Boolean) {
                return (Boolean) fromInstance ? new AtomicLong(1L) : new AtomicLong(0L);
            } else if (fromInstance instanceof AtomicBoolean) {
                return ((AtomicBoolean) fromInstance).get() ? new AtomicLong(1L) : new AtomicLong(0L);
            } else if (fromInstance instanceof Calendar) {
                return new AtomicLong(((Calendar) fromInstance).getTime().getTime());
            }
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("value [" + name(fromInstance) + "] could not be converted to an 'AtomicLong'", e);
        }
        nope(fromInstance, "AtomicLong");
        return null;
    }

    /**
     * Convert from the passed in instance to an AtomicBoolean.  If null is passed in, new AtomicBoolean(false) is
     * returned. Possible inputs are String, all primitive/primitive wrappers, boolean, AtomicBoolean,
     * (false=0, true=1), and all Atomic*s.
     */
    public static AtomicBoolean convert2AtomicBoolean(Object fromInstance) {
        if (fromInstance == null) {
            return new AtomicBoolean(false);
        }
        return convertToAtomicBoolean(fromInstance);
    }

    /**
     * Convert from the passed in instance to an AtomicBoolean.  If null is passed in, null is returned. Possible inputs
     * are String, all primitive/primitive wrappers, boolean, AtomicBoolean, (false=0, true=1), and all Atomic*s.
     */
    public static AtomicBoolean convertToAtomicBoolean(Object fromInstance) {
        if (fromInstance instanceof String) {
            if (MetaUtils.isEmpty((String) fromInstance)) {
                return new AtomicBoolean(false);
            }
            String value = (String) fromInstance;
            return new AtomicBoolean("true".equalsIgnoreCase(value));
        } else if (fromInstance instanceof AtomicBoolean) {   // return a clone of the AtomicBoolean because it is mutable
            return new AtomicBoolean(((AtomicBoolean) fromInstance).get());
        } else if (fromInstance instanceof Boolean) {
            return new AtomicBoolean((Boolean) fromInstance);
        } else if (fromInstance instanceof Number) {
            return new AtomicBoolean(((Number) fromInstance).longValue() != 0);
        } else if (fromInstance instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) fromInstance;
            return convert2AtomicBoolean(map.get("value"));
        }
        nope(fromInstance, "AtomicBoolean");
        return null;
    }

    private static String nope(Object fromInstance, String targetType) {
        if (fromInstance == null) {
            return null;
        }
        throw new JsonIoException("Unsupported value type [" + name(fromInstance) + "] attempting to convert to '" + targetType + "'");
    }

    private static String name(Object fromInstance) {
        if (fromInstance == null) {
            return "null";
        }
        return fromInstance.getClass().getName() + " (" + fromInstance + ")";
    }

    /**
     * @param localDate A Java LocalDate
     * @return a long representing the localDate as the number of milliseconds since the
     * number of milliseconds since Jan 1, 1970
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
