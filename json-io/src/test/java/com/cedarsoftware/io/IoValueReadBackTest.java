package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.annotation.IoTypeInfo;
import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A class with an {@code @IoValue} method is written as the single value that method returns, and where the class is
 * declared (a field, a List&lt;Email&gt;, a map's values or keys, an array, the class asked for at the root) nothing
 * else: "a@x.com", not {"@type":..,"value":"a@x.com"}. Up to 4.117.0 that value did not read back: an error in a
 * field, an array or at the root, and a String in a list or a map. It is now built by the class's one-parameter
 * {@code @IoCreator}, in JSON and in TOON, as the {"@type":..,"value":..} form always was.
 *
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
class IoValueReadBackTest {
    public static class Email {
        private final String address;

        @IoCreator
        public Email(@IoProperty("address") String address) {
            this.address = address;
        }

        @IoValue
        public String toValue() {
            return address;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Email && Objects.equals(((Email) o).address, address);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(address);
        }

        @Override
        public String toString() {
            return "Email(" + address + ")";
        }
    }

    /** A number, built by a static factory that takes an int: the value read (a Long) is converted to it. */
    public static class Cents {
        private final int amount;

        private Cents(int amount) {
            this.amount = amount;
        }

        @IoCreator
        public static Cents of(@IoProperty("amount") int amount) {
            return new Cents(amount);
        }

        @IoValue
        public int toValue() {
            return amount;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Cents && ((Cents) o).amount == amount;
        }

        @Override
        public int hashCode() {
            return amount;
        }

        @Override
        public String toString() {
            return "Cents(" + amount + ")";
        }
    }

    /** Written as one value, but its creator takes two: nothing can read it back from that value. */
    public static class Money {
        private final long cents;
        private final String currency;

        @IoCreator
        public Money(@IoProperty("cents") long cents, @IoProperty("currency") String currency) {
            this.cents = cents;
            this.currency = currency;
        }

        @IoValue
        public String toValue() {
            return cents + " " + currency;
        }
    }

    /** A one-parameter creator and no @IoValue method: written as its fields, so a bare value is not its form. */
    public static class UserId {
        private final long id;

        @IoCreator
        public UserId(@IoProperty("id") long id) {
            this.id = id;
        }
    }

    public static class UserIdHolder {
        public UserId user;
    }

    public static class Holder {
        public Email email;
        public Cents cents;
        public Object any;
        public List<Email> emails = new ArrayList<>();
        public Map<String, Cents> centsByName = new LinkedHashMap<>();
        public Map<Email, String> byEmail = new LinkedHashMap<>();
        public Email[] emailArray;
    }

    public static class MoneyHolder {
        public Money money;
    }

    /** Fields declared Object, each naming the class to read a value without a type as. */
    public static class Defaulted {
        @IoTypeInfo(Email.class)
        public Object email;
        @IoTypeInfo(Money.class)
        public Object money;
    }

    private static Holder holder() {
        Holder h = new Holder();
        h.email = new Email("a@x.com");
        h.cents = Cents.of(150);
        h.any = new Email("b@x.com");
        h.emails.add(new Email("c@x.com"));
        h.emails.add(new Email("d@x.com"));
        h.centsByName.put("small", Cents.of(1));
        h.centsByName.put("large", Cents.of(1000000));
        h.byEmail.put(new Email("k@x.com"), "key");
        h.emailArray = new Email[] {new Email("e@x.com")};
        return h;
    }

    private static void assertSameHolder(Holder expected, Holder actual, String text) {
        assertEquals(expected.email, actual.email, text);
        assertEquals(expected.cents, actual.cents, text);
        assertEquals(expected.emails, actual.emails, text);
        for (Object e : actual.emails) {
            assertInstanceOf(Email.class, e, text);
        }
        assertEquals(expected.centsByName, actual.centsByName, text);
        for (Object c : ((Map<?, ?>) actual.centsByName).values()) {
            assertInstanceOf(Cents.class, c, text);
        }
        assertEquals(expected.byEmail, actual.byEmail, text);
        for (Object k : ((Map<?, ?>) actual.byEmail).keySet()) {
            assertInstanceOf(Email.class, k, text);
        }
        assertEquals(1, actual.emailArray.length, text);
        assertEquals(expected.emailArray[0], actual.emailArray[0], text);
    }

    @Test
    void jsonReadsTheValueBackWhereTheClassIsDeclared() {
        WriteOptions[] all = {new WriteOptionsBuilder().build(),
                new WriteOptionsBuilder().showTypeInfoNever().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()};
        for (WriteOptions options : all) {
            Holder h = holder();
            String json = JsonIo.toJson(h, options);
            Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
            assertSameHolder(h, back, json);
        }
    }

    @Test
    void theDefaultModeWritesTheValueAloneWhereTheClassIsDeclared() {
        Holder h = holder();
        String json = JsonIo.toJson(h, null);
        assertTrue(json.contains("\"email\":\"a@x.com\""), json);
        assertTrue(json.contains("\"cents\":150"), json);
        assertTrue(json.contains("\"emails\":[\"c@x.com\",\"d@x.com\"]"), json);
        Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
        assertSameHolder(h, back, json);
        assertEquals(h.any, back.any, json);      // in an Object field it carries its type, as before
    }

    @Test
    void toonReadsTheValueBackWhereTheClassIsDeclared() {
        String toon = "email: a@x.com\n"
                + "cents: 150\n"
                + "emails[2]: c@x.com,d@x.com\n"
                + "centsByName:\n"
                + "  small: 1\n"
                + "  large: 1000000\n"
                + "byEmail:\n"
                + "  k@x.com: key\n"
                + "emailArray[1]: e@x.com\n";
        Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
        assertSameHolder(holder(), back, toon);
    }

    @Test
    void theClassAskedForAtTheRootIsBuiltFromTheValue() {
        assertEquals(new Email("r@x.com"), JsonIo.toJava("\"r@x.com\"", null).asClass(Email.class));
        assertEquals(Cents.of(7), JsonIo.toJava("7", null).asClass(Cents.class));
        assertEquals(new Email("r@x.com"), JsonIo.fromToon("r@x.com", null).asClass(Email.class));
        assertEquals(Cents.of(7), JsonIo.fromToon("7", null).asClass(Cents.class));
    }

    /** An empty string is the value only for a creator that takes text; elsewhere it is null, as before. */
    @Test
    void anEmptyStringIsTheValueOnlyWhenTheCreatorTakesText() {
        Holder back = JsonIo.toJava("{\"email\":\"\",\"cents\":\"\"}", null).asClass(Holder.class);
        assertEquals(new Email(""), back.email);
        assertNull(back.cents);

        Holder h = new Holder();
        h.email = new Email("");
        String json = JsonIo.toJson(h, null);
        assertEquals(new Email(""), JsonIo.toJava(json, null).asClass(Holder.class).email, json);
    }

    @Test
    void aClassWhoseCreatorDoesNotTakeTheValueSaysWhy() {
        MoneyHolder h = new MoneyHolder();
        h.money = new Money(150, "USD");
        String json = JsonIo.toJson(h, null);
        assertTrue(json.contains("\"money\":\"150 USD\""), json);
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava(json, null).asClass(MoneyHolder.class));
        assertTrue(e.getMessage().startsWith(MoneyHolder.class.getName() + ".money: "), e.getMessage());
        assertTrue(e.getMessage().contains("@IoValue method toValue()"), e.getMessage());
        assertTrue(e.getMessage().contains("its @IoCreator has 2"), e.getMessage());

        e = assertThrows(JsonIoException.class, () -> JsonIo.toJava("\"150 USD\"", null).asClass(Money.class));
        assertTrue(e.getMessage().contains("@IoValue method toValue()"), e.getMessage());

        assertNull(JsonIo.toJava("{\"money\":\"\"}", null).asClass(MoneyHolder.class).money);   // as before
    }

    /**
     * {@code @IoTypeInfo} names the class a value without a type is read as: an @IoValue class is built from the value.
     * One whose creator does not take the value leaves the value in the field, as before: the field is an Object.
     */
    @Test
    void aFieldDeclaredObjectReadsTheClassItsIoTypeInfoNames() {
        Defaulted back = JsonIo.toJava("{\"email\":\"a@x.com\",\"money\":\"150 USD\"}", null).asClass(Defaulted.class);
        assertEquals(new Email("a@x.com"), back.email);
        assertEquals("150 USD", back.money);
    }

    /** Only an @IoValue class is read from a bare value: a creator alone does not make one its form. */
    @Test
    void aClassWithoutIoValueIsNotBuiltFromABareValue() {
        assertEquals(5, JsonIo.toJava("{\"user\":{\"id\":5}}", null).asClass(UserIdHolder.class).user.id);
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"user\":5}", null).asClass(UserIdHolder.class));
        assertTrue(e.getMessage().contains("Unsupported conversion"), e.getMessage());
        e = assertThrows(JsonIoException.class, () -> JsonIo.toJava("5", null).asClass(UserId.class));
        assertTrue(e.getMessage().startsWith("Return type mismatch"), e.getMessage());
    }
}
