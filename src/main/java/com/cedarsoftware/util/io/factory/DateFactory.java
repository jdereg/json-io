package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateFactory implements JsonReader.ClassFactory {
    private static final String DAYS = "(monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thur|thu|friday|fri|saturday|sat|sunday|sun)"; // longer before shorter matters
    private static final String MOS = "(January|Jan|February|Feb|March|Mar|April|Apr|May|June|Jun|July|Jul|August|Aug|September|Sept|Sep|October|Oct|November|Nov|December|Dec)";
    private static final Pattern datePattern1 = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern datePattern2 = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})");
    private static final Pattern datePattern3 = Pattern.compile(MOS + "[ ]*[,]?[ ]*(\\d{1,2})(st|nd|rd|th|)[ ]*[,]?[ ]*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern4 = Pattern.compile("(\\d{1,2})(st|nd|rd|th|)[ ]*[,]?[ ]*" + MOS + "[ ]*[,]?[ ]*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern5 = Pattern.compile("(\\d{4})[ ]*[,]?[ ]*" + MOS + "[ ]*[,]?[ ]*(\\d{1,2})(st|nd|rd|th|)", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern6 = Pattern.compile(DAYS + "[ ]+" + MOS + "[ ]+(\\d{1,2})[ ]+(\\d{2}:\\d{2}:\\d{2})[ ]+[A-Z]{1,4}\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern timePattern1 = Pattern.compile("(\\d{2})[.:](\\d{2})[.:](\\d{2})[.](\\d{1,10})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern timePattern2 = Pattern.compile("(\\d{2})[.:](\\d{2})[.:](\\d{2})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern timePattern3 = Pattern.compile("(\\d{2})[.:](\\d{2})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern dayPattern = Pattern.compile(DAYS, Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> months = new LinkedHashMap<>();

    static {
        // Month name to number map
        // This class is only used in DateReader so far.  We should move it in there until we need it elsewhere.
        months.put("jan", "1");
        months.put("january", "1");
        months.put("feb", "2");
        months.put("february", "2");
        months.put("mar", "3");
        months.put("march", "3");
        months.put("apr", "4");
        months.put("april", "4");
        months.put("may", "5");
        months.put("jun", "6");
        months.put("june", "6");
        months.put("jul", "7");
        months.put("july", "7");
        months.put("aug", "8");
        months.put("august", "8");
        months.put("sep", "9");
        months.put("sept", "9");
        months.put("september", "9");
        months.put("oct", "10");
        months.put("october", "10");
        months.put("nov", "11");
        months.put("november", "11");
        months.put("dec", "12");
        months.put("december", "12");
    }

    @Override
    public Object newInstance(Class<?> c, JsonObject jObj) {
        Object value = jObj.getValue();

        if (value instanceof String) {
            return fromString((String) value);
        }

        if (value instanceof Number) {
            return fromNumber((Number) value);
        }

        return fromJsonObject(c, jObj);
    }

    protected Object fromString(String value) {
        return parseDate(value);
    }

    protected Object fromNumber(Number value) {
        return new Date(value.longValue());
    }

    //  if date comes in as full json object its timestamp type
    //  due to
    protected Object fromJsonObject(Class<?> c, JsonObject object) {
        Object time = object.get("time");
        if (time == null) {
            throw new JsonIoException("'time' field must be specified'");
        }
        Object nanos = object.get("nanos");

        Timestamp timestamp = new Timestamp(Long.parseLong((String) time));

        if (nanos == null) {
            return timestamp;
        }

        timestamp.setNanos(Integer.parseInt((String) nanos));
        return timestamp;
    }

    public static Date parseDate(String dateStr) {
        dateStr = dateStr.trim();
        if (dateStr.isEmpty()) {
            return null;
        }

        // Determine which date pattern (Matcher) to use
        Matcher matcher = datePattern1.matcher(dateStr);

        String year, month = null, day, mon = null, remains;

        if (matcher.find()) {
            year = matcher.group(1);
            month = matcher.group(2);
            day = matcher.group(3);
            remains = matcher.replaceFirst("");
        } else {
            matcher = datePattern2.matcher(dateStr);
            if (matcher.find()) {
                month = matcher.group(1);
                day = matcher.group(2);
                year = matcher.group(3);
                remains = matcher.replaceFirst("");
            } else {
                matcher = datePattern3.matcher(dateStr);
                if (matcher.find()) {
                    mon = matcher.group(1);
                    day = matcher.group(2);
                    year = matcher.group(4);
                    remains = matcher.replaceFirst("");
                } else {
                    matcher = datePattern4.matcher(dateStr);
                    if (matcher.find()) {
                        day = matcher.group(1);
                        mon = matcher.group(3);
                        year = matcher.group(4);
                        remains = matcher.replaceFirst("");
                    } else {
                        matcher = datePattern5.matcher(dateStr);
                        if (matcher.find()) {
                            year = matcher.group(1);
                            mon = matcher.group(2);
                            day = matcher.group(3);
                            remains = matcher.replaceFirst("");
                        } else {
                            matcher = datePattern6.matcher(dateStr);
                            if (!matcher.find()) {
                                throw new JsonIoException("Unable to parse: " + dateStr);
                            }
                            year = matcher.group(5);
                            mon = matcher.group(2);
                            day = matcher.group(3);
                            remains = matcher.group(4);
                        }
                    }
                }
            }
        }

        if (mon != null) {   // Month will always be in Map, because regex forces this.
            month = months.get(mon.trim().toLowerCase());
        }

        // Determine which date pattern (Matcher) to use
        String hour = null, min = null, sec = "00", milli = "0", tz = null;
        remains = remains.trim();
        matcher = timePattern1.matcher(remains);
        if (matcher.find()) {
            hour = matcher.group(1);
            min = matcher.group(2);
            sec = matcher.group(3);
            milli = matcher.group(4);
            if (matcher.groupCount() > 4) {
                tz = matcher.group(5);
            }
        } else {
            matcher = timePattern2.matcher(remains);
            if (matcher.find()) {
                hour = matcher.group(1);
                min = matcher.group(2);
                sec = matcher.group(3);
                if (matcher.groupCount() > 3) {
                    tz = matcher.group(4);
                }
            } else {
                matcher = timePattern3.matcher(remains);
                if (matcher.find()) {
                    hour = matcher.group(1);
                    min = matcher.group(2);
                    if (matcher.groupCount() > 2) {
                        tz = matcher.group(3);
                    }
                } else {
                    matcher = null;
                }
            }
        }

        if (matcher != null) {
            remains = matcher.replaceFirst("");
        }

        // Clear out day of week (mon, tue, wed, ...)
        if (remains != null && remains.length() > 0) {
            Matcher dayMatcher = dayPattern.matcher(remains);
            if (dayMatcher.find()) {
                remains = dayMatcher.replaceFirst("").trim();
            }
        }
        if (remains != null && remains.length() > 0) {
            remains = remains.trim();
            if (!remains.equals(",") && (!remains.equals("T"))) {
                throw new JsonIoException("Issue parsing data/time, other characters present: " + remains);
            }
        }

        Calendar c = Calendar.getInstance();
        c.clear();
        if (tz != null) {
            if ("z".equalsIgnoreCase(tz)) {
                c.setTimeZone(TimeZone.getTimeZone("GMT"));
            } else {
                c.setTimeZone(TimeZone.getTimeZone("GMT" + tz));
            }
        }

        // Regex prevents these from ever failing to parse
        int y = Integer.parseInt(year);
        int m = Integer.parseInt(month) - 1;    // months are 0-based
        int d = Integer.parseInt(day);

        if (m < 0 || m > 11) {
            throw new JsonIoException("Month must be between 1 and 12 inclusive, date: " + dateStr);
        }
        if (d < 1 || d > 31) {
            throw new JsonIoException("Day must be between 1 and 31 inclusive, date: " + dateStr);
        }

        if (matcher == null) {   // no [valid] time portion
            c.set(y, m, d);
        } else {
            // Regex prevents these from ever failing to parse.
            int h = Integer.parseInt(hour);
            int mn = Integer.parseInt(min);
            int s = Integer.parseInt(sec);
            int ms = Integer.parseInt(milli);

            if (h > 23) {
                throw new JsonIoException("Hour must be between 0 and 23 inclusive, time: " + dateStr);
            }
            if (mn > 59) {
                throw new JsonIoException("Minute must be between 0 and 59 inclusive, time: " + dateStr);
            }
            if (s > 59) {
                throw new JsonIoException("Second must be between 0 and 59 inclusive, time: " + dateStr);
            }

            // regex enforces millis to number
            c.set(y, m, d, h, mn, s);
            c.set(Calendar.MILLISECOND, ms);
        }
        return c.getTime();
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}

