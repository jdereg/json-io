package com.cedarsoftware.util.reflect;

import com.cedarsoftware.util.io.MetaUtils;
import com.cedarsoftware.util.io.TestUtil;
import com.cedarsoftware.util.io.WriteOptions;
import com.cedarsoftware.util.io.WriteOptionsBuilder;
import com.cedarsoftware.util.reflect.accessors.FieldAccessor;
import com.cedarsoftware.util.reflect.accessors.MethodAccessor;
import com.cedarsoftware.util.reflect.filters.models.Car;
import com.cedarsoftware.util.reflect.filters.models.ColorEnum;
import com.cedarsoftware.util.reflect.filters.models.Entity;
import com.cedarsoftware.util.reflect.filters.models.Part;
import com.cedarsoftware.util.reflect.filters.models.PrivateFinalObject;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
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

    @Test
    void testCloningObject_withFieldBlacklistedAtSubclassOfEntity_fieldsAreAvailableOnOtherSubsOfEntity() {
        WriteOptions options = new WriteOptionsBuilder()
                .withDefaultOptimizations()
                .withFieldNameBlackList(Car.class, MetaUtils.listOf("id", "updated", "created"))
                .build();

        Car initial = createCar();

        String json = TestUtil.toJson(initial, options);
        Car car = TestUtil.toJava(json);

        assertThat(car.getId()).isNull();
        assertThat(car.getUpdated()).isNull();
        assertThat(car.getCreated()).isNull();

        for (Part part : car.getInstalledParts()) {
            assertThat(part.getId()).isNotNull();
            assertThat(part.getUpdated()).isNotNull();
            assertThat(part.getCreated()).isNotNull();
        }

        for (Part part : car.getAccessories()) {
            assertThat(part.getId()).isNotNull();
            assertThat(part.getUpdated()).isNotNull();
            assertThat(part.getCreated()).isNotNull();
        }
    }

    @Test
    void testCloningObject_fieldBlacklistedAtSuperClassEntity_isNotAvailableOnAnySubClass() {
        WriteOptions options = new WriteOptionsBuilder()
                .withDefaultOptimizations()
                .withFieldNameBlackList(Entity.class, MetaUtils.listOf("id", "updated", "created"))
                .build();

        Car initial = createCar();

        String json = TestUtil.toJson(initial, options);
        Car car = TestUtil.toJava(json);

        assertThat(car.getId()).isNull();
        assertThat(car.getUpdated()).isNull();
        assertThat(car.getCreated()).isNull();

        for (Part part : car.getInstalledParts()) {
            assertThat(part.getId()).isNull();
            assertThat(part.getUpdated()).isNull();
            assertThat(part.getCreated()).isNull();
        }

        for (Part part : car.getAccessories()) {
            assertThat(part.getId()).isNull();
            assertThat(part.getUpdated()).isNull();
            assertThat(part.getCreated()).isNull();
        }
    }

    private Car createCar() {
        Car car = create(new Car(), "VIN123456");
        car.setMake("Ford");
        car.setModel("Mustang");
        car.setColor(ColorEnum.RED);
        car.setInstalledParts(createParts());
        car.setAccessories(createAccessories());
        return car;
    }

    private List<Part> createParts() {
        Part tire = create(new Part(), "P55");
        tire.setName("Pirelli");
        tire.setDescription("Pirelli blah blah blah");

        Part steeringWheel = create(new Part(), "Yoke");
        steeringWheel.setDescription("Yolk style steering wheel");
        steeringWheel.setName("Yolk");

        Part exhaust = create(new Part(), "B77");
        exhaust.setDescription("Brand Dual Exhast");
        exhaust.setName("Brando");

        return MetaUtils.listOf(tire, steeringWheel, exhaust);
    }

    private List<Part> createAccessories() {
        Part tire = create(new Part(), "HM29");
        tire.setName("Heated Mirrors");
        tire.setDescription("Heated Mirrors");

        Part steeringWheel = create(new Part(), "HS31");
        steeringWheel.setDescription("Heated Seats");
        steeringWheel.setName("Heated Seats");

        Part exhaust = create(new Part(), "FM22");
        exhaust.setDescription("Floor Mats");
        exhaust.setName("Floor Mats");

        return MetaUtils.listOf(tire, steeringWheel, exhaust);
    }

    private <T extends Entity> T create(T entity, String id) {
        entity.setId(id);
        entity.setCreated(new Date());
        entity.setUpdated(entity.getCreated());
        return entity;
    }
}
