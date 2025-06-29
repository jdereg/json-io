package com.cedarsoftware.io;

import com.cedarsoftware.io.JsonReader.DefaultReferenceTracker;
import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolverValueToTargetTest {

    private static class TestResolver extends Resolver {
        TestResolver(ReadOptions options) {
            super(options, new DefaultReferenceTracker(options), new Converter(options.getConverterOptions()));
        }

        boolean callValueToTarget(JsonObject obj) {
            return valueToTarget(obj);
        }

        @Override
        public void traverseFields(JsonObject jsonObj) {
        }

        @Override
        protected Object readWithFactoryIfExists(Object o, Type compType) {
            return null;
        }

        @Override
        protected void traverseCollection(JsonObject jsonObj) {
        }

        @Override
        protected void traverseArray(JsonObject jsonObj) {
        }

        @Override
        protected Object resolveArray(Type suggestedType, List<Object> list) {
            return null;
        }
    }

    private final TestResolver resolver = new TestResolver(new ReadOptionsBuilder().build());

    @Test
    void nullTypeReturnsFalse() {
        JsonObject obj = new JsonObject();
        assertThat(resolver.callValueToTarget(obj)).isFalse();
    }

    @Test
    void convertsPrimitiveArray() {
        JsonObject nested = new JsonObject();
        nested.setType(int.class);
        nested.setValue("3");

        JsonObject arrayObj = new JsonObject();
        arrayObj.setType(int[].class);
        arrayObj.setItems(new Object[]{1, "2", nested});

        assertThat(resolver.callValueToTarget(arrayObj)).isTrue();
        assertThat(arrayObj.getTarget()).isInstanceOf(int[].class);
        assertThat((int[]) arrayObj.getTarget()).containsExactly(1, 2, 3);
    }

    @Test
    void conversionFailureWrapsException() {
        JsonObject arrayObj = new JsonObject();
        arrayObj.setType(int[].class);
        arrayObj.setItems(new Object[]{"bad"});

        assertThatThrownBy(() -> resolver.callValueToTarget(arrayObj))
                .isInstanceOf(JsonIoException.class);
    }
}
