package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code WriteOptionsBuilder.toonLegacyForms(true)} writes TOON with the forms of spec v3 only, which json-io
 * 4.113.0 and earlier read: no keyed table, no nested field group, and a table as a list item. The default writes
 * the forms of spec v4. json-io reads both, whichever is set.
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
class ToonLegacyFormsTest {

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /** One document with each of the three differences. */
    private static Object document() {
        return map(
                "servers", map("alpha", map("host", "a", "port", 1L), "beta", map("host", "b", "port", 2L)),
                "orders", listOf(
                        map("id", 1L, "customer", map("name", "Ada")),
                        map("id", 2L, "customer", map("name", "Bob"))),
                "grid", listOf(listOf(map("x", 1L), map("x", 2L))),
                "flat", listOf(map("x", 1L, "y", 2L), map("x", 3L, "y", 4L)));
    }

    private static final String V4 = "servers[2:]{host,port}:\n"
            + "  alpha: a,1\n"
            + "  beta: b,2\n"
            + "orders[2]{id,customer{name}}:\n"
            + "  1,Ada\n"
            + "  2,Bob\n"
            + "grid[1]:\n"
            + "  - [2]:\n"
            + "    - x: 1\n"
            + "    - x: 2\n"
            + "flat[2]{x,y}:\n"
            + "  1,2\n"
            + "  3,4";

    private static final String V3 = "servers:\n"
            + "  alpha:\n"
            + "    host: a\n"
            + "    port: 1\n"
            + "  beta:\n"
            + "    host: b\n"
            + "    port: 2\n"
            + "orders[2]:\n"
            + "  - id: 1\n"
            + "    customer:\n"
            + "      name: Ada\n"
            + "  - id: 2\n"
            + "    customer:\n"
            + "      name: Bob\n"
            + "grid[1]:\n"
            + "  - [2]{x}:\n"
            + "    1\n"
            + "    2\n"
            + "flat[2]{x,y}:\n"
            + "  1,2\n"
            + "  3,4";

    @Test
    void theDefaultWritesTheFormsOfSpecV4() {
        assertFalse(new WriteOptionsBuilder().build().isToonLegacyForms());
        assertFalse(WriteOptionsBuilder.getDefaultWriteOptions().isToonLegacyForms());

        assertEquals(V4, JsonIo.toToon(document(), null));
        assertEquals(V4, JsonIo.toToon(document(), new WriteOptionsBuilder().build()));
        assertEquals(V4, JsonIo.toToon(document(), new WriteOptionsBuilder().toonLegacyForms(false).build()));
    }

    @Test
    void theOptionWritesTheFormsOfSpecV3() {
        WriteOptions legacy = new WriteOptionsBuilder().toonLegacyForms(true).build();

        assertTrue(legacy.isToonLegacyForms());
        assertEquals(V3, JsonIo.toToon(document(), legacy));
    }

    /** Whichever way it was written, json-io reads the same document, in strict and in non-strict mode. */
    @Test
    void bothAreReadAlike() {
        String expected = JsonIo.toJson(document(), new WriteOptionsBuilder().showTypeInfoNever().build());

        for (String toon : new String[] {V4, V3}) {
            for (boolean strict : new boolean[] {true, false}) {
                Object back = JsonIo.fromToon(toon, new ReadOptionsBuilder().strictToon(strict).build())
                        .asClass(Object.class);
                assertEquals(expected, JsonIo.toJson(back, new WriteOptionsBuilder().showTypeInfoNever().build()),
                        (strict ? "strict\n" : "non-strict\n") + toon);
            }
        }
    }

    @Test
    void aCopyOfTheOptionsKeepsTheSetting() {
        WriteOptions legacy = new WriteOptionsBuilder().toonLegacyForms(true).build();

        assertTrue(new WriteOptionsBuilder(legacy).build().isToonLegacyForms());
        assertFalse(new WriteOptionsBuilder(legacy).toonLegacyForms(false).build().isToonLegacyForms());
        assertFalse(new WriteOptionsBuilder(new WriteOptionsBuilder().build()).build().isToonLegacyForms());
        assertEquals(V3, JsonIo.toToon(document(), new WriteOptionsBuilder(legacy).build()));
    }

    @Test
    void thePermanentSettingIsTheDefaultOfEveryNewBuilder() {
        try {
            WriteOptionsBuilder.addPermanentToonLegacyForms(true);
            WriteOptions options = new WriteOptionsBuilder().build();
            assertTrue(options.isToonLegacyForms());
            assertEquals(V3, JsonIo.toToon(document(), options));
            assertFalse(new WriteOptionsBuilder().toonLegacyForms(false).build().isToonLegacyForms());
        } finally {
            WriteOptionsBuilder.addPermanentToonLegacyForms(false);
        }
        assertFalse(new WriteOptionsBuilder().build().isToonLegacyForms());
        assertEquals(V4, JsonIo.toToon(document(), new WriteOptionsBuilder().build()));
    }
}
