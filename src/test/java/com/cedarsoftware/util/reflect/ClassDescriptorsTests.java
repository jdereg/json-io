package com.cedarsoftware.util.reflect;

import com.cedarsoftware.util.reflect.accessors.FieldAccessor;
import com.cedarsoftware.util.reflect.accessors.MethodAccessor;
import com.cedarsoftware.util.reflect.filters.models.ColorEnum;
import com.cedarsoftware.util.reflect.filters.models.PrivateFinalObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClassDescriptorsTests {
    @Test
    void testGetClassDescriptor_whereSomeFieldsHaveAccessorsAndOthersDoNot() {

        ClassDescriptor descriptor = ClassDescriptors.instance().getClassDescriptor(PrivateFinalObject.class);
        Map<String, Accessor> accessors = descriptor.getAccessors();

        assertThat(accessors).hasSize(4);
        assertThat(accessors.get("x")).isInstanceOf(FieldAccessor.class);
        assertThat(accessors.get("y")).isInstanceOf(FieldAccessor.class);
        assertThat(accessors.get("key")).isInstanceOf(MethodAccessor.class);
        assertThat(accessors.get("flatuated")).isInstanceOf(MethodAccessor.class);
    }

    @Test
    void testGetClassDescriptor_onEnumDefinitionClass() {
        ClassDescriptor descriptor = ClassDescriptors.instance().getClassDescriptor(ColorEnum.class);
        Map<String, Accessor> accessors = descriptor.getAccessors();

        assertThat(accessors).hasSize(0);
    }

    @Test
    void testGetClassDescriptor_onEnumClass() {
        ClassDescriptor descriptor = ClassDescriptors.instance().getClassDescriptor(Enum.class);
        Map<String, Accessor> accessors = descriptor.getAccessors();

        assertThat(accessors).hasSize(1);
        assertThat(accessors.get("name")).isInstanceOf(MethodAccessor.class);
    }
}
