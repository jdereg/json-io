package com.cedarsoftware.io.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.cedarsoftware.io.reflect.filters.models.Car;
import com.cedarsoftware.io.reflect.filters.models.Part;
import com.cedarsoftware.io.reflect.filters.models.GetMethodTestObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessorTests {

    @Test
    void getMethodHandle_returnsHandleWhenAccessible() throws Throwable {
        Field field = GetMethodTestObject.class.getField("test5");
        Accessor accessor = Accessor.createFieldAccessor(field, "test5");
        assertThat(accessor.getMethodHandle()).isNotNull();

        GetMethodTestObject obj = new GetMethodTestObject();
        Object value = accessor.getMethodHandle().invoke(obj);
        assertThat(value).isEqualTo("foo");
    }

    @Test
    void getGenericType_returnsParameterizedType() throws Exception {
        Field field = Car.class.getDeclaredField("installedParts");
        Accessor accessor = Accessor.createFieldAccessor(field, "installedParts");
        Type type = accessor.getGenericType();

        assertThat(type).isInstanceOf(ParameterizedType.class);
        ParameterizedType pType = (ParameterizedType) type;
        assertThat(pType.getActualTypeArguments()[0]).isEqualTo(Part.class);
    }
}
