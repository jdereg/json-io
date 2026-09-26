package com.cedarsoftware.io;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JsonObject past the linear-search threshold looks keys up through a lazily built hash index. The parser's fast
 * append, appendFieldForParser(), did not add to that index -- harmless while no lookup had built it yet, but strict
 * TOON checks every key for a duplicate before appending it, which builds the index as soon as the object passes the
 * threshold. Every field after that was then invisible to get() and containsKey() while keySet() still listed it: a
 * ClassFactory reading a 12-field strict-TOON object by name got null for fields 10 to 12.
 * <p>
 * The threshold is a JVM-wide setting that other tests change, so these tests size their objects from it.
 */
class JsonObjectAppendIndexTest {

    private static int pastThreshold(int extra) {
        return JsonObject.getLinearSearchThreshold() + extra;
    }

    @Test
    void aFieldAppendedAfterALookupIsFound() {
        JsonObject jo = new JsonObject();
        int n = pastThreshold(2);
        for (int i = 0; i < n; i++) {
            jo.appendFieldForParser("f" + i, i);
        }
        assertEquals(0, jo.get("f0"));      // this lookup builds the index

        jo.appendFieldForParser("late", "here");
        assertEquals("here", jo.get("late"));
        assertTrue(jo.containsKey("late"));
    }

    @Test
    void everyListedKeyCanBeLookedUp() {
        JsonObject jo = new JsonObject();
        int n = pastThreshold(6);
        for (int i = 0; i < n; i++) {
            if (i == 3) {
                assertTrue(jo.containsKey("f0"));
            }
            jo.appendFieldForParser("f" + i, i);
            jo.containsKey("absent");       // a lookup between appends, as strict TOON makes
        }
        for (Object key : jo.keySet()) {
            assertTrue(jo.containsKey(key), "keySet() lists " + key + " but containsKey() denies it");
            assertEquals(Integer.parseInt(((String) key).substring(1)), jo.get(key));
        }
    }

    @Test
    void theCachedHashFollowsAnAppend() {
        JsonObject jo = new JsonObject();
        jo.appendFieldForParser("a", 1);
        jo.hashCode();                      // cached
        jo.appendFieldForParser("b", 2);

        JsonObject same = new JsonObject();
        same.appendFieldForParser("a", 1);
        same.appendFieldForParser("b", 2);
        assertEquals(same.hashCode(), jo.hashCode(), "hashCode() must not keep the value cached before the append");
    }

    public static class Wide {
        final String[] v;

        Wide(String[] v) {
            this.v = v;
        }
    }

    /** A user factory reading the fields by name, as ClassFactory implementations do. */
    static class WideFactory implements ClassFactory {
        int count;

        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
            String[] v = new String[count];
            for (int i = 0; i < count; i++) {
                v[i] = (String) jObj.get("a" + (i + 1));
            }
            return new Wide(v);
        }

        @Override
        public boolean isObjectFinal() {
            return true;
        }
    }

    @Test
    void aClassFactorySeesEveryFieldOfAStrictToonObject() {
        for (boolean strict : new boolean[]{false, true}) {
            WideFactory factory = new WideFactory();
            ReadOptions options = new ReadOptionsBuilder().strictToon(strict).addClassFactory(Wide.class, factory).build();
            int count = pastThreshold(4);       // build() has just applied the options' threshold JVM-wide
            factory.count = count;

            StringBuilder toon = new StringBuilder("@type: " + Wide.class.getName() + "\n");
            String[] expected = new String[count];
            for (int i = 1; i <= count; i++) {
                toon.append('a').append(i).append(": v").append(i).append('\n');
                expected[i - 1] = "v" + i;
            }
            Wide w = JsonIo.fromToon(toon.toString(), options).asClass(Wide.class);
            assertArrayEquals(expected, w.v, "strictToon=" + strict + ": " + Arrays.toString(w.v));
        }
    }
}
