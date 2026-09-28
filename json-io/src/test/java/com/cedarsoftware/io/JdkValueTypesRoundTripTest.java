package com.cedarsoftware.io;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.sql.Time;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JDK value types that did not round trip, now carried by java-util's conversions (4.113.0):
 * <ul>
 *   <li>{@code java.sql.Time} read back as a {@code java.util.Date} -- the wrong class. Jackson reads it.</li>
 *   <li>{@code InetAddress} was written as an empty object on JDK 9+ (its fields are hidden) and could not be read.
 *       Jackson reads it, but resolves a host name it is given; json-io reads IP addresses only and keeps a host
 *       name beside its address, looking nothing up.</li>
 *   <li>{@code LongAdder} and {@code DoubleAdder} were written as empty objects, on every JDK: the count was lost.
 *       Jackson writes the number but cannot read it back.</li>
 * </ul>
 */
class JdkValueTypesRoundTripTest {

    public static class Holder {
        public Time time;
        public InetAddress address;
        public LongAdder hits;
        public DoubleAdder total;
        public Object any;
        public Time[] times;
    }

    private static final Time TIME = new Time(Time.valueOf("13:14:15").getTime() + 123);

    private static LongAdder hits(long n) {
        LongAdder adder = new LongAdder();
        adder.add(n);
        return adder;
    }

    private static <T> T roundTrip(Object value, Class<T> type) {
        return JsonIo.toJava(JsonIo.toJson(value, null), null).asClass(type);
    }

    @Test
    void sqlTime() {
        Time read = roundTrip(TIME, Time.class);
        assertThat(read).isExactlyInstanceOf(Time.class).isEqualTo(TIME);

        Holder holder = new Holder();
        holder.time = TIME;
        holder.any = TIME;
        holder.times = new Time[]{TIME};
        Holder back = roundTrip(holder, Holder.class);
        assertThat(back.time).isExactlyInstanceOf(Time.class).isEqualTo(TIME);
        assertThat(back.any).isExactlyInstanceOf(Time.class).isEqualTo(TIME);
        assertThat(back.times[0]).isExactlyInstanceOf(Time.class).isEqualTo(TIME);

        // As json-io wrote it before, and as Jackson writes it
        String old = "{\"@type\":\"java.sql.Time\",\"date\":" + TIME.getTime() + "}";
        assertThat(JsonIo.toJava(old, null).asClass(Time.class)).isExactlyInstanceOf(Time.class).isEqualTo(TIME);
        assertThat(JsonIo.toJava("{\"time\":\"13:14:15\"}", null).asClass(Holder.class).time)
                .isEqualTo(Time.valueOf("13:14:15"));
    }

    @Test
    void inetAddress() throws Exception {
        InetAddress plain = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        String json = JsonIo.toJson(plain, null);
        assertThat(json).contains("\"10.0.0.1\"");
        assertThat(JsonIo.toJava(json, null).asClass(InetAddress.class)).isInstanceOf(Inet4Address.class).isEqualTo(plain);

        InetAddress named = InetAddress.getByAddress("build-server", new byte[]{10, 0, 0, 7});
        InetAddress namedBack = roundTrip(named, InetAddress.class);
        assertThat(namedBack.toString()).isEqualTo("build-server/10.0.0.7");

        InetAddress v6 = InetAddress.getByName("2001:db8::1");
        assertThat(roundTrip(v6, InetAddress.class)).isInstanceOf(Inet6Address.class).isEqualTo(v6);

        Holder holder = new Holder();
        holder.address = named;
        holder.any = plain;
        Holder back = roundTrip(holder, Holder.class);
        assertThat(back.address.toString()).isEqualTo("build-server/10.0.0.7");
        assertThat(back.any).isEqualTo(plain);

        // As Jackson writes an address without a name
        assertThat(JsonIo.toJava("{\"address\":\"10.0.0.1\"}", null).asClass(Holder.class).address).isEqualTo(plain);
        // A bare host name is refused, not looked up
        assertThatThrownBy(() -> JsonIo.toJava("{\"address\":\"localhost\"}", null).asClass(Holder.class))
                .isInstanceOf(JsonIoException.class)
                .hasStackTraceContaining("does not look up host names");
    }

    @Test
    void longAndDoubleAdders() {
        assertThat(roundTrip(hits(5), LongAdder.class).sum()).isEqualTo(5L);
        DoubleAdder total = new DoubleAdder();
        total.add(2.5);
        assertThat(roundTrip(total, DoubleAdder.class).sum()).isEqualTo(2.5);

        Holder holder = new Holder();
        holder.hits = hits(42);
        holder.total = total;
        holder.any = hits(7);
        String json = JsonIo.toJson(holder, null);
        assertThat(json).contains("\"hits\":42").contains("\"total\":2.5");
        Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
        assertThat(back.hits.sum()).isEqualTo(42L);
        assertThat(back.total.sum()).isEqualTo(2.5);
        assertThat(back.any).isInstanceOf(LongAdder.class);
        assertThat(((LongAdder) back.any).sum()).isEqualTo(7L);

        // As Jackson writes them
        assertThat(JsonIo.toJava("{\"hits\":9,\"total\":1.5}", null).asClass(Holder.class).hits.sum()).isEqualTo(9L);

        Holder toonBack = JsonIo.fromToon(JsonIo.toToon(holder, null), null).asClass(Holder.class);
        assertThat(toonBack.hits.sum()).isEqualTo(42L);
        assertThat(toonBack.total.sum()).isEqualTo(2.5);
    }
}
