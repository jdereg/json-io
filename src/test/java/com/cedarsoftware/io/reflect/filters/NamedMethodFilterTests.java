package com.cedarsoftware.io.reflect.filters;

import java.util.List;

import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;
import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.filters.method.NamedMethodFilter;
import com.cedarsoftware.io.reflect.filters.models.GetMethodTestObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamedMethodFilterTests {

    @Test
    void filter_returnsTrueWhenMatch() {
        NamedMethodFilter filter = new NamedMethodFilter(GetMethodTestObject.class, "getTest5");
        assertThat(filter.filter(GetMethodTestObject.class, "getTest5")).isTrue();
    }

    @Test
    void filter_returnsFalseWhenClassOrMethodDiffer() {
        NamedMethodFilter filter = new NamedMethodFilter(GetMethodTestObject.class, "getTest5");
        assertThat(filter.filter(GetMethodTestObject.class, "getTest4")).isFalse();
        assertThat(filter.filter(Object.class, "getTest5")).isFalse();
    }

    @Test
    void filter_handlesNullArgumentsGracefully() {
        NamedMethodFilter filter = new NamedMethodFilter(GetMethodTestObject.class, "getTest5");
        assertThat(filter.filter(null, null)).isFalse();
        assertThat(filter.filter(GetMethodTestObject.class, null)).isFalse();
        assertThat(filter.filter(null, "getTest5")).isFalse();
    }

    @Test
    void integration_namedMethodFilterRemovesAccessor() {
        WriteOptions options = new WriteOptionsBuilder()
                .addNamedMethodFilter("noGet", GetMethodTestObject.class, "getTest5")
                .build();

        List<Accessor> accessors = options.getAccessorsForClass(GetMethodTestObject.class);
        assertThat(accessors).hasSize(5);
        Accessor last = accessors.get(4);
        assertThat(last.getFieldOrMethodName()).isEqualTo("test5");
        assertThat(last.isMethod()).isFalse();
    }
}

