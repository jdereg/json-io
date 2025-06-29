package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MapResolver#traverseCollection(JsonObject)}.
 */
class MapResolverTraverseCollectionTest {

    private MapResolver newResolver(ReadOptions options, ReferenceTracker refs) {
        Converter converter = new Converter(options.getConverterOptions());
        return new MapResolver(options, refs, converter);
    }

    @Test
    void testEarlyExitWhenFinished() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        ReferenceTracker refs = new JsonReader.DefaultReferenceTracker(readOptions);
        MapResolver resolver = newResolver(options, refs);

        JsonObject json = new JsonObject();
        json.setItems(new Object[]{"x"});
        List<Object> list = new ArrayList<>();
        json.setTarget(list);
        json.setFinished();

        resolver.traverseCollection(json);

        assertTrue(json.isFinished());
        assertTrue(list.isEmpty());
    }

    static class SimpleBean {
        int x;
    }

    @Test
    void testTraverseCollectionAllBranches() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();
        ReadOptions readOptions2 = new ReadOptionsBuilder().build();
        JsonReader.DefaultReferenceTracker refs = new JsonReader.DefaultReferenceTracker(readOptions2);
        MapResolver resolver = newResolver(options, refs);

        JsonObject json = new JsonObject();
        List<Object> list = new ArrayList<>();
        json.setTarget(list);

        JsonObject refResolved = new JsonObject();
        refResolved.setTarget("target");
        refs.put(1L, refResolved);

        JsonObject refMissing = new JsonObject();
        refs.put(2L, refMissing);

        JsonObject elementRefResolved = new JsonObject();
        elementRefResolved.setReferenceId(1L);

        JsonObject elementRefMissing = new JsonObject();
        elementRefMissing.setReferenceId(2L);

        JsonObject nonRef = new JsonObject();
        nonRef.setType(String.class);
        nonRef.setValue("str");

        JsonObject custom = new JsonObject();
        custom.setType(SimpleBean.class);
        custom.put("x", 5);

        Object[] items = new Object[] {
                null,
                "text",
                Boolean.TRUE,
                7L,
                new String[]{"a", "b"},
                elementRefResolved,
                elementRefMissing,
                nonRef,
                custom
        };
        json.setItems(items);

        resolver.traverseCollection(json);

        assertThat(list).hasSize(items.length);
        assertNull(list.get(0));
        assertEquals("text", list.get(1));
        assertEquals(Boolean.TRUE, list.get(2));
        assertEquals(7L, list.get(3));
        assertTrue(list.get(4).getClass().isArray());
        assertEquals("target", list.get(5));
        assertNull(list.get(6));
        assertEquals("str", list.get(7));
        assertNull(list.get(8));

        assertEquals(1, resolver.unresolvedRefs.size());
    }

    private enum TestEnum { A }

    @Test
    void testEnumSetBranch() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        ReferenceTracker refs = new JsonReader.DefaultReferenceTracker(readOptions);
        MapResolver resolver = newResolver(options, refs);

        JsonObject json = new JsonObject();
        EnumSet<TestEnum> set = EnumSet.noneOf(TestEnum.class);
        json.setTarget(set);
        JsonObject element = new JsonObject();
        element.setType(TestEnum.class);
        json.setItems(new Object[]{element});

        resolver.traverseCollection(json);

        assertThat(set).isEmpty();
    }

    @Test
    void testCreateInstanceWhenCollectionNull() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        ReferenceTracker refs = new JsonReader.DefaultReferenceTracker(readOptions);
        MapResolver resolver = newResolver(options, refs);

        JsonObject json = new JsonObject();
        json.setType(ArrayList.class);
        json.setItems(new Object[]{"a"});

        resolver.traverseCollection(json);

        @SuppressWarnings("unchecked")
        Collection<Object> target = (Collection<Object>) json.getTarget();
        assertThat(target).containsExactly("a");
    }
}
