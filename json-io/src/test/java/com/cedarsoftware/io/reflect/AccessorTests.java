package com.cedarsoftware.io.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.cedarsoftware.io.reflect.filters.models.Car;
import com.cedarsoftware.io.reflect.filters.models.Part;
import com.cedarsoftware.io.reflect.filters.models.GetMethodTestObject;
import com.cedarsoftware.io.reflect.filters.models.PrimitiveAccessorTestObject;
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

    @Test
    void primitiveFieldAccessorsReturnUnboxedValues() throws Exception {
        PrimitiveAccessorTestObject obj = new PrimitiveAccessorTestObject();

        Accessor booleanAccessor = fieldAccessor("booleanValue");
        assertThat(booleanAccessor.getBoolean(obj)).isTrue();
        assertThat(booleanAccessor.retrieve(obj)).isEqualTo(Boolean.TRUE);

        Accessor byteAccessor = fieldAccessor("byteValue");
        assertThat(byteAccessor.getByte(obj)).isEqualTo((byte) 12);
        assertThat(byteAccessor.retrieve(obj)).isEqualTo((byte) 12);

        Accessor charAccessor = fieldAccessor("charValue");
        assertThat(charAccessor.getChar(obj)).isEqualTo('J');
        assertThat(charAccessor.retrieve(obj)).isEqualTo('J');

        Accessor shortAccessor = fieldAccessor("shortValue");
        assertThat(shortAccessor.getShort(obj)).isEqualTo((short) 32000);
        assertThat(shortAccessor.retrieve(obj)).isEqualTo((short) 32000);

        Accessor intAccessor = fieldAccessor("intValue");
        assertThat(intAccessor.getInt(obj)).isEqualTo(123456789);
        assertThat(intAccessor.retrieve(obj)).isEqualTo(123456789);

        Accessor longAccessor = fieldAccessor("longValue");
        assertThat(longAccessor.getLong(obj)).isEqualTo(9876543210L);
        assertThat(longAccessor.retrieve(obj)).isEqualTo(9876543210L);

        Accessor floatAccessor = fieldAccessor("floatValue");
        assertThat(floatAccessor.getFloat(obj)).isEqualTo(3.25f);
        assertThat(floatAccessor.retrieve(obj)).isEqualTo(3.25f);

        Accessor doubleAccessor = fieldAccessor("doubleValue");
        assertThat(doubleAccessor.getDouble(obj)).isEqualTo(6.5d);
        assertThat(doubleAccessor.retrieve(obj)).isEqualTo(6.5d);
    }

    @Test
    void primitiveMethodAccessorsReturnUnboxedValues() throws Exception {
        PrimitiveAccessorTestObject obj = new PrimitiveAccessorTestObject();

        Accessor booleanAccessor = methodAccessor("booleanValue", "isBooleanValue");
        assertThat(booleanAccessor).isNotNull();
        assertThat(booleanAccessor.getBoolean(obj)).isTrue();
        assertThat(booleanAccessor.retrieve(obj)).isEqualTo(Boolean.TRUE);

        Accessor byteAccessor = methodAccessor("byteValue", "getByteValue");
        assertThat(byteAccessor).isNotNull();
        assertThat(byteAccessor.getByte(obj)).isEqualTo((byte) 12);
        assertThat(byteAccessor.retrieve(obj)).isEqualTo((byte) 12);

        Accessor charAccessor = methodAccessor("charValue", "getCharValue");
        assertThat(charAccessor).isNotNull();
        assertThat(charAccessor.getChar(obj)).isEqualTo('J');
        assertThat(charAccessor.retrieve(obj)).isEqualTo('J');

        Accessor shortAccessor = methodAccessor("shortValue", "getShortValue");
        assertThat(shortAccessor).isNotNull();
        assertThat(shortAccessor.getShort(obj)).isEqualTo((short) 32000);
        assertThat(shortAccessor.retrieve(obj)).isEqualTo((short) 32000);

        Accessor intAccessor = methodAccessor("intValue", "getIntValue");
        assertThat(intAccessor).isNotNull();
        assertThat(intAccessor.getInt(obj)).isEqualTo(123456789);
        assertThat(intAccessor.retrieve(obj)).isEqualTo(123456789);

        Accessor longAccessor = methodAccessor("longValue", "getLongValue");
        assertThat(longAccessor).isNotNull();
        assertThat(longAccessor.getLong(obj)).isEqualTo(9876543210L);
        assertThat(longAccessor.retrieve(obj)).isEqualTo(9876543210L);

        Accessor floatAccessor = methodAccessor("floatValue", "getFloatValue");
        assertThat(floatAccessor).isNotNull();
        assertThat(floatAccessor.getFloat(obj)).isEqualTo(3.25f);
        assertThat(floatAccessor.retrieve(obj)).isEqualTo(3.25f);

        Accessor doubleAccessor = methodAccessor("doubleValue", "getDoubleValue");
        assertThat(doubleAccessor).isNotNull();
        assertThat(doubleAccessor.getDouble(obj)).isEqualTo(6.5d);
        assertThat(doubleAccessor.retrieve(obj)).isEqualTo(6.5d);
    }

    private static Accessor fieldAccessor(String fieldName) throws Exception {
        Field field = PrimitiveAccessorTestObject.class.getDeclaredField(fieldName);
        return Accessor.createFieldAccessor(field, fieldName);
    }

    private static Accessor methodAccessor(String fieldName, String methodName) throws Exception {
        Field field = PrimitiveAccessorTestObject.class.getDeclaredField(fieldName);
        return Accessor.createMethodAccessor(field, methodName, fieldName);
    }
}
