package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XMLGregorianCalendar and javax.xml.datatype.Duration -- what JAXB generates for xs:dateTime, xs:date and
 * xs:duration -- are written as their XML Schema lexical form under their public type, and read back through
 * java-util's Converter, on every JDK (4.116.0).
 * <p>
 * Their runtime classes are the JDK's internal com.sun.org.apache.xerces.internal.jaxp.datatype.XMLGregorianCalendarImpl
 * and DurationImpl. Through 4.115.0 json-io wrote them field by field under those names: correct on JDK 8 and 11,
 * zeros on JDK 17+ (the class is not opened to it), and from 4.113.0 not at all -- java-util refuses the names, and
 * the write threw "For security reasons, cannot load: ...XMLGregorianCalendarImpl".
 */
class XmlDatatypeRoundTripTest {
    private static final DatatypeFactory FACTORY = newFactory();
    private static final WriteOptions NEVER = new WriteOptionsBuilder().showTypeInfoNever().build();

    private static DatatypeFactory newFactory() {
        try {
            return DatatypeFactory.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static XMLGregorianCalendar calendar(String text) {
        return FACTORY.newXMLGregorianCalendar(text);
    }

    /** The shape of a JAXB-generated class. */
    public static class Claim {
        public XMLGregorianCalendar received;       // xs:dateTime
        public XMLGregorianCalendar lossDate;       // xs:date
        public Duration waitingPeriod;              // xs:duration
        public Object anything;
        public XMLGregorianCalendar[] history;
        public List<XMLGregorianCalendar> dates;
        public Map<String, Duration> periods;
    }

    private static Claim claim() {
        Claim claim = new Claim();
        claim.received = calendar("2026-10-15T10:20:30.123-04:00");
        claim.lossDate = calendar("2020-06-28");
        claim.waitingPeriod = FACTORY.newDuration("P1DT2H");
        claim.anything = calendar("10:20:30Z");
        claim.history = new XMLGregorianCalendar[]{calendar("2026-01-02T03:04:05Z"), calendar("--10-15")};
        claim.dates = new ArrayList<>();
        claim.dates.add(calendar("2026-10"));
        claim.dates.add(calendar("-0044-03-15"));
        claim.periods = new LinkedHashMap<>();
        claim.periods.put("grace", FACTORY.newDuration("-P1Y2M"));
        claim.periods.put("tiny", FACTORY.newDuration("PT0.5S"));
        return claim;
    }

    /** Each value as its text: XMLGregorianCalendar.equals() calls two zones' renderings of one instant equal. */
    private static void assertSameValues(Claim expected, Claim actual) {
        assertThat(actual.received.toXMLFormat()).isEqualTo(expected.received.toXMLFormat());
        assertThat(actual.lossDate.toXMLFormat()).isEqualTo(expected.lossDate.toXMLFormat());
        assertThat(actual.waitingPeriod.toString()).isEqualTo(expected.waitingPeriod.toString());
        assertThat(actual.anything).isInstanceOf(XMLGregorianCalendar.class);
        assertThat(((XMLGregorianCalendar) actual.anything).toXMLFormat())
                .isEqualTo(((XMLGregorianCalendar) expected.anything).toXMLFormat());
        assertThat(actual.history).hasSize(expected.history.length);
        for (int i = 0; i < expected.history.length; i++) {
            assertThat(actual.history[i].toXMLFormat()).isEqualTo(expected.history[i].toXMLFormat());
        }
        assertThat(actual.dates).hasSize(expected.dates.size());
        for (int i = 0; i < expected.dates.size(); i++) {
            assertThat(actual.dates.get(i).toXMLFormat()).isEqualTo(expected.dates.get(i).toXMLFormat());
        }
        assertThat(actual.periods.keySet()).containsExactlyElementsOf(expected.periods.keySet());
        for (String key : expected.periods.keySet()) {
            assertThat(actual.periods.get(key).toString()).isEqualTo(expected.periods.get(key).toString());
        }
    }

    @Test
    void aJaxbStyleObjectRoundTrips() {
        Claim claim = claim();
        String json = JsonIo.toJson(claim, null);
        assertThat(json).doesNotContain("com.sun").doesNotContain("Impl");
        assertThat(json).contains("\"received\":{\"@type\":\"XMLGregorianCalendar\",\"value\":\"2026-10-15T10:20:30.123-04:00\"}");
        assertThat(json).contains("\"waitingPeriod\":{\"@type\":\"javax.xml.datatype.Duration\",\"value\":\"P1DT2H\"}");
        assertSameValues(claim, JsonIo.toJava(json, null).asClass(Claim.class));

        WriteOptions pretty = new WriteOptionsBuilder().prettyPrint(true).build();
        assertSameValues(claim, JsonIo.toJava(JsonIo.toJson(claim, pretty), null).asClass(Claim.class));
        WriteOptions json5 = new WriteOptionsBuilder().json5().build();
        assertSameValuesUntyped(claim, JsonIo.toJava(JsonIo.toJson(claim, json5), null).asClass(Claim.class));
    }

    /**
     * With no type information -- showTypeInfoNever(), json5(), TOON -- an Object field holding one comes back as its
     * text, since nothing says what it was (a TimeZone does the same); every declared type comes back as itself.
     */
    private static void assertSameValuesUntyped(Claim expected, Claim back) {
        assertThat(back.anything).isEqualTo(((XMLGregorianCalendar) expected.anything).toXMLFormat());
        back.anything = calendar((String) back.anything);
        assertSameValues(expected, back);
    }

    @Test
    void withoutTypeInformationEachIsItsText() {
        Claim claim = new Claim();
        claim.received = calendar("2026-10-15T10:20:30.123-04:00");
        claim.lossDate = calendar("2020-06-28");
        claim.waitingPeriod = FACTORY.newDuration("P1DT2H");
        String json = JsonIo.toJson(claim, NEVER);
        assertThat(json).isEqualTo("{\"received\":\"2026-10-15T10:20:30.123-04:00\",\"lossDate\":\"2020-06-28\","
                + "\"waitingPeriod\":\"P1DT2H\",\"anything\":null,\"history\":null,\"dates\":null,\"periods\":null}");

        Claim back = JsonIo.toJava(json, null).asClass(Claim.class);
        assertThat(back.received.toXMLFormat()).isEqualTo("2026-10-15T10:20:30.123-04:00");
        assertThat(back.lossDate.toXMLFormat()).isEqualTo("2020-06-28");
        assertThat(back.waitingPeriod.toString()).isEqualTo("P1DT2H");

        Claim full = claim();
        assertSameValuesUntyped(full, JsonIo.toJava(JsonIo.toJson(full, NEVER), null).asClass(Claim.class));
    }

    @Test
    void asTheRootAndInsideUntypedContainers() {
        XMLGregorianCalendar calendar = calendar("2026-10-15T10:20:30Z");
        String json = JsonIo.toJson(calendar, null);
        assertThat(json).isEqualTo("{\"@type\":\"XMLGregorianCalendar\",\"value\":\"2026-10-15T10:20:30Z\"}");
        XMLGregorianCalendar back = JsonIo.toJava(json, null).asClass(null);
        assertThat(back.toXMLFormat()).isEqualTo("2026-10-15T10:20:30Z");

        Duration duration = FACTORY.newDuration("P3D");
        json = JsonIo.toJson(duration, null);
        assertThat(json).isEqualTo("{\"@type\":\"javax.xml.datatype.Duration\",\"value\":\"P3D\"}");
        assertThat(JsonIo.toJava(json, null).asClass(null).toString()).isEqualTo("P3D");

        Object[] array = {calendar, duration, "text"};
        Object[] arrayBack = JsonIo.toJava(JsonIo.toJson(array, null), null).asClass(Object[].class);
        assertThat(((XMLGregorianCalendar) arrayBack[0]).toXMLFormat()).isEqualTo("2026-10-15T10:20:30Z");
        assertThat(arrayBack[1].toString()).isEqualTo("P3D");

        List<Object> list = new ArrayList<>();
        list.add(calendar);
        list.add(duration);
        List<?> listBack = JsonIo.toJava(JsonIo.toJson(list, null), null).asClass(List.class);
        assertThat(((XMLGregorianCalendar) listBack.get(0)).toXMLFormat()).isEqualTo("2026-10-15T10:20:30Z");
        assertThat(listBack.get(1).toString()).isEqualTo("P3D");
    }

    /** As a Calendar is: one instance in two places is written twice, never as an @id and a @ref. */
    @Test
    void oneInstanceInTwoPlacesIsWrittenTwice() {
        Claim claim = new Claim();
        claim.received = calendar("2026-10-15T10:20:30Z");
        claim.anything = claim.received;
        claim.waitingPeriod = FACTORY.newDuration("P1D");
        claim.periods = new LinkedHashMap<>();
        claim.periods.put("same", claim.waitingPeriod);
        String json = JsonIo.toJson(claim, null);
        assertThat(json).doesNotContain("@id").doesNotContain("@ref");
        Claim back = JsonIo.toJava(json, null).asClass(Claim.class);
        assertThat(((XMLGregorianCalendar) back.anything).toXMLFormat()).isEqualTo(back.received.toXMLFormat());
        assertThat(back.periods.get("same").toString()).isEqualTo("P1D");
    }

    /** JSON json-io 4.112.0 wrote on JDK 8 and 11, verbatim: the internal class name and the fields it held. */
    @Test
    void whatJsonIo4112WroteStillReads() {
        String json = "{\"@type\":\"com.cedarsoftware.io.XmlDatatypeRoundTripTest$Claim\","
                + "\"received\":{\"@type\":\"com.sun.org.apache.xerces.internal.jaxp.datatype.XMLGregorianCalendarImpl\","
                + "\"eon\":null,\"year\":2026,\"month\":10,\"day\":15,\"timezone\":-240,\"hour\":10,\"minute\":20,"
                + "\"second\":30,\"fractionalSecond\":\"0.123\"},"
                + "\"anything\":{\"@type\":\"com.sun.org.apache.xerces.internal.jaxp.datatype.XMLGregorianCalendarImpl\","
                + "\"eon\":null,\"year\":2020,\"month\":6,\"day\":28,\"timezone\":-2147483648,\"hour\":-2147483648,"
                + "\"minute\":-2147483648,\"second\":-2147483648,\"fractionalSecond\":null},"
                + "\"waitingPeriod\":{\"@type\":\"com.sun.org.apache.xerces.internal.jaxp.datatype.DurationImpl\","
                + "\"signum\":1,\"years\":null,\"months\":null,\"days\":\"1\",\"hours\":\"2\",\"minutes\":null,\"seconds\":null}}";
        Claim back = JsonIo.toJava(json, null).asClass(Claim.class);
        assertThat(back.received.toXMLFormat()).isEqualTo("2026-10-15T10:20:30.123-04:00");
        assertThat(((XMLGregorianCalendar) back.anything).toXMLFormat()).isEqualTo("2020-06-28");
        assertThat(back.waitingPeriod.toString()).isEqualTo("P1DT2H");
    }

    @Test
    void mapsModeWritesBackWhatItRead() {
        String json = JsonIo.toJson(claim(), null);
        Object maps = JsonIo.toMaps(json, null).asClass(null);
        assertThat(JsonIo.toJson(maps, null)).isEqualTo(json);
    }

    @Test
    void toonRoundTrips() {
        Claim claim = claim();
        String toon = JsonIo.toToon(claim, null);
        assertThat(toon).doesNotContain("com.sun");
        assertSameValuesUntyped(claim, JsonIo.fromToon(toon, null).asClass(Claim.class));
    }

    /** 500 calendars from a fixed seed, every kind and with and without a zone, come back as the text they were. */
    @Test
    void randomCalendarsComeBackAsTheyWere() {
        Random random = new Random(20261015L);
        List<XMLGregorianCalendar> calendars = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            XMLGregorianCalendar c = FACTORY.newXMLGregorianCalendar();
            int kind = random.nextInt(3);           // 0 dateTime, 1 date, 2 time
            if (kind != 2) {
                c.setYear(random.nextBoolean() ? 1 + random.nextInt(9999) : -(1 + random.nextInt(9999)));
                c.setMonth(1 + random.nextInt(12));
                c.setDay(1 + random.nextInt(28));
            }
            if (kind != 1) {
                c.setTime(random.nextInt(24), random.nextInt(60), random.nextInt(60));
                if (random.nextBoolean()) {
                    c.setMillisecond(random.nextInt(1000));
                }
            }
            if (random.nextBoolean()) {
                c.setTimezone((random.nextInt(57) - 28) * 30);
            }
            calendars.add(c);
        }
        Claim claim = new Claim();
        claim.dates = calendars;
        for (WriteOptions options : new WriteOptions[]{null, NEVER}) {
            Claim back = JsonIo.toJava(JsonIo.toJson(claim, options), null).asClass(Claim.class);
            assertThat(back.dates).hasSize(500);
            for (int i = 0; i < 500; i++) {
                assertThat(back.dates.get(i).toXMLFormat()).isEqualTo(calendars.get(i).toXMLFormat());
            }
        }
    }
}
