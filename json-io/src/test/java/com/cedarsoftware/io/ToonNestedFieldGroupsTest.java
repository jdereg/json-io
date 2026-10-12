package com.cedarsoftware.io;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A field of a tabular header may carry its own field list: {@code orders[2]{id,customer{name,country},total}:}
 * (TOON spec 9.3, new in spec v4.0). The nested field group stands for an object in every row, and a row holds one
 * cell per leaf field in the header's depth-first order.
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
class ToonNestedFieldGroupsTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON = new WriteOptionsBuilder().showTypeInfoNever().build();

    static class Customer {
        String name;
        String country;
    }

    static class Order {
        long id;
        Customer customer;
        double total;
    }

    static class Shop {
        List<Order> orders;
    }

    static class Parcel {
        long id;
        Object owner;
    }

    static class Depot {
        List<Parcel> parcels;
    }

    private static Object read(String toon, ReadOptions options) {
        return JsonIo.fromToon(toon, options).asClass(Object.class);
    }

    /** The value as JSON without type information: what was read, whatever Java classes hold it. */
    private static String json(Object value) {
        return JsonIo.toJson(value, PLAIN_JSON);
    }

    private static void assertReads(Object expected, String toon) {
        for (ReadOptions options : BOTH_MODES) {
            String mode = options.isStrictToon() ? "strict" : "non-strict";
            assertEquals(json(expected), json(read(toon, options)), mode + "\n" + toon);
        }
    }

    private static void assertRejectedInBothModes(String toon, String message) {
        for (ReadOptions options : BOTH_MODES) {
            assertThatThrownBy(() -> read(toon, options))
                    .as(toon)
                    .isInstanceOf(JsonIoException.class)
                    .hasMessageContaining(message);
        }
    }

    @Test
    void aNestedFieldGroupIsAnObjectInEveryRow() {
        String toon = "orders[2]{id,customer{name,country},total}:\n"
                + "  1,Ada,DK,99\n"
                + "  2,Bob,UK,149";

        assertReads(mapOf("orders", listOf(
                mapOf("id", 1L, "customer", mapOf("name", "Ada", "country", "DK"), "total", 99L),
                mapOf("id", 2L, "customer", mapOf("name", "Bob", "country", "UK"), "total", 149L))), toon);
    }

    @Test
    void groupsNestToAnyDepthAndStandSideBySide() {
        String toon = "shipments[1]{id,geo{point{lat,lon}},sender{id,city},receiver{id,city}}:\n"
                + "  s1,1.5,2.5,ACME,Berlin,Globex,Oslo";

        assertReads(mapOf("shipments", listOf(mapOf(
                "id", "s1",
                "geo", mapOf("point", mapOf("lat", 1.5, "lon", 2.5)),
                "sender", mapOf("id", "ACME", "city", "Berlin"),
                "receiver", mapOf("id", "Globex", "city", "Oslo")))), toon);
    }

    @Test
    void theHeadersDelimiterSplitsEveryLevelAndEveryRow() {
        Object expected = mapOf("orders", listOf(
                mapOf("id", 1L, "customer", mapOf("name", "Ada, Countess", "country", "DK"), "total", 99L)));

        assertReads(expected, "orders[1|]{id|customer{name|country}|total}:\n  1|Ada, Countess|DK|99");
        assertReads(expected, "orders[1\t]{id\tcustomer{name\tcountry}\ttotal}:\n  1\tAda, Countess\tDK\t99");
    }

    @Test
    void aQuotedNameMayHoldBracesDelimitersAndColons() {
        String toon = "items[1]{\"a{b}\",c{\"x,y\",\"p:q\"},\"tab\\tbed\"}:\n  1,2,3,4";

        assertReads(mapOf("items", listOf(
                mapOf("a{b}", 1L, "c", mapOf("x,y", 2L, "p:q", 3L), "tab\tbed", 4L))), toon);
    }

    /** The header is read from the line: its colon is not the first colon of the line. */
    @Test
    void anUnquotedNameMayHoldAColon() {
        assertReads(mapOf("items", listOf(mapOf("2key", 1L, "x:y", 2L, "n", mapOf("p:q", 3L)))),
                "items[1]{2key,x:y,n{p:q}}:\n  1,2,3");
    }

    @Test
    void spacesAroundANameAreTrimmed() {
        assertReads(mapOf("items", listOf(mapOf("a", 1L, "n", mapOf("x", 2L), "b", 3L))),
                "items[1]{ a , n{x} , b }:\n  1,2,3");
    }

    @Test
    void cellsAreReadAsInAnyRow() {
        String toon = "rows[2]{id,v{text,flag,amount,note}}:\n"
                + "  1,\"a,b {c}\",true,-2.5,null\n"
                + "  2,\"say \\\"hi\\\"\",false,007,\"\"";

        assertReads(mapOf("rows", listOf(
                mapOf("id", 1L, "v", mapOf("text", "a,b {c}", "flag", true, "amount", -2.5, "note", null)),
                mapOf("id", 2L, "v", mapOf("text", "say \"hi\"", "flag", false, "amount", "007", "note", "")))),
                toon);
    }

    @Test
    void theHeaderMayBeTheRootOrTheFirstFieldOfAListItem() {
        assertReads(listOf(mapOf("id", 1L, "c", mapOf("x", 2L)), mapOf("id", 3L, "c", mapOf("x", 4L))),
                "[2]{id,c{x}}:\n  1,2\n  3,4");

        String toon = "batches[2]:\n"
                + "  - orders[2]{id,c{x}}:\n"
                + "      1,2\n"
                + "      3,4\n"
                + "    status: ok\n"
                + "  - status: none";
        assertReads(mapOf("batches", listOf(
                mapOf("orders", listOf(mapOf("id", 1L, "c", mapOf("x", 2L)), mapOf("id", 3L, "c", mapOf("x", 4L))),
                        "status", "ok"),
                mapOf("status", "none"))), toon);
    }

    @Test
    void theRowsFillNestedJavaObjects() {
        String toon = "orders[2]{id,customer{name,country},total}:\n"
                + "  1,Ada,DK,99.5\n"
                + "  2,Bob,UK,149";

        for (ReadOptions options : BOTH_MODES) {
            Shop shop = JsonIo.fromToon(toon, options).asClass(Shop.class);

            assertEquals(2, shop.orders.size());
            assertEquals(1L, shop.orders.get(0).id);
            assertEquals("Ada", shop.orders.get(0).customer.name);
            assertEquals("DK", shop.orders.get(0).customer.country);
            assertEquals(99.5, shop.orders.get(0).total);
            assertEquals("Bob", shop.orders.get(1).customer.name);
            assertEquals(149.0, shop.orders.get(1).total);
        }
    }

    /** json-io's own meta keys work inside a group as they do in a flat header: the group says what it is. */
    @Test
    void aTypeNamedInsideAGroupIsTheGroupsType() {
        String toon = "parcels[1]{id,owner{@type,name,country}}:\n"
                + "  1," + Customer.class.getName() + ",Ada,DK";

        for (ReadOptions options : BOTH_MODES) {
            Depot depot = JsonIo.fromToon(toon, options).asClass(Depot.class);

            Customer owner = assertInstanceOf(Customer.class, depot.parcels.get(0).owner);
            assertEquals("Ada", owner.name);
            assertEquals("DK", owner.country);
        }
    }

    /** Without every cell the nesting cannot be rebuilt, so this is an error in non-strict mode too. */
    @Test
    void aRowHoldsExactlyOneCellPerLeafField() {
        assertRejectedInBothModes("items[1]{a,n{x,y}}:\n  1,2", "width mismatch at line 2, expected 3 values, got 2");
        assertRejectedInBothModes("items[1]{a,n{x}}:\n  1,2,3", "width mismatch at line 2, expected 2 values, got 3");
        assertRejectedInBothModes("items[2]{a,n{x}}:\n  1,2\n  3", "width mismatch at line 3");
    }

    @Test
    void strictModeRejectsAMalformedFieldList() {
        String[] malformed = {
                "a[1]{b{c}:\n  1",          // a brace without its partner
                "a[1]{b{c}}}:\n  1",        // one brace too many
                "a[1]{b{c}d}:\n  1",        // text between a group and the next delimiter
                "a[1]{b{}}:\n  1",          // a group with no fields
                "a[1]{x,{c}}:\n  1,2",      // a group with no name
                "a[1]{b {c}}:\n  1",        // whitespace between a name and its group
                "a[1]{b|c{d}}:\n  1,2",     // a delimiter the header did not declare
        };
        for (String toon : malformed) {
            assertThatThrownBy(() -> read(toon, STRICT)).as(toon).isInstanceOf(JsonIoException.class);
        }
    }

    /** In strict mode a name may not repeat within one field list; at two levels it is not a repeat. */
    @Test
    void aNameRepeatedWithinOneFieldList() {
        assertThatThrownBy(() -> read("items[1]{a,a{x}}:\n  1,2", STRICT))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Duplicate field name 'a'");
        assertThatThrownBy(() -> read("items[0]{n{x,x}}:", STRICT))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Duplicate field name 'x'");
        assertThatThrownBy(() -> read("items[1]{a,a}:\n  1,2", STRICT))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Duplicate field name 'a'");

        assertReads(mapOf("items", listOf(mapOf("x", 1L, "n", mapOf("x", 2L)))), "items[1]{x,n{x}}:\n  1,2");

        // Non-strict mode: the last one wins.
        Map<?, ?> root = (Map<?, ?>) read("items[1]{a,a,b,b{x}}:\n  1,2,3,4", LENIENT);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) root.get("items")).get(0);
        assertEquals(2L, item.get("a"));
        assertEquals(json(mapOf("x", 4L)), json(item.get("b")));
    }

    /** As for any malformed header behind a key, non-strict mode reads the line as a key and its value. */
    @Test
    void nonStrictModeReadsAMalformedHeaderAsAKeyAndItsValue() {
        Map<?, ?> root = (Map<?, ?>) read("a[1]{b{c}d}: 5", LENIENT);

        assertEquals(5L, root.get("a[1]{b{c}d}"));
    }

    @Test
    void theNestingOfAFieldListIsLimited() {
        StringBuilder header = new StringBuilder("a[1]{x");
        for (int i = 0; i < 20; i++) {
            header.append("{x");
        }
        for (int i = 0; i < 20; i++) {
            header.append('}');
        }
        header.append("}:\n  1");
        ReadOptions shallow = new ReadOptionsBuilder().maxDepth(10).build();

        assertThatThrownBy(() -> read(header.toString(), shallow))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("nested deeper than 10 levels");
        assertEquals(1, ((List<?>) ((Map<?, ?>) read(header.toString(), LENIENT)).get("a")).size());
    }

    /** A header without a nested group reads exactly as it did. */
    @Test
    void aFlatHeaderIsUnchanged() {
        String toon = "rows[2]{id,\"full name\",\"a,b\"}:\n"
                + "  1,Ada,x\n"
                + "  2,\"Bob, Jr.\",y\n"
                + "next: 5";

        assertReads(mapOf(
                "rows", listOf(mapOf("id", 1L, "full name", "Ada", "a,b", "x"),
                        mapOf("id", 2L, "full name", "Bob, Jr.", "a,b", "y")),
                "next", 5L), toon);
        // A short row is still only an error in strict mode.
        assertEquals(1, ((List<?>) ((Map<?, ?>) read("rows[1]{a,b,c}:\n  1,2", LENIENT)).get("rows")).size());
        assertThatThrownBy(() -> read("rows[1]{a,b,c}:\n  1,2", STRICT)).hasMessageContaining("width mismatch");
    }
}
