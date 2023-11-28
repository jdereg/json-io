package com.cedarsoftware.util.reflect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cedarsoftware.util.io.MetaUtils;
import com.cedarsoftware.util.io.TestUtil;
import com.cedarsoftware.util.io.WriteOptions;
import com.cedarsoftware.util.reflect.filters.models.Car;
import com.cedarsoftware.util.reflect.filters.models.ColorEnum;
import com.cedarsoftware.util.reflect.filters.models.Entity;
import com.cedarsoftware.util.reflect.filters.models.Part;
import com.cedarsoftware.util.reflect.filters.models.PrivateFinalObject;

class ClassDescriptorsTests {
    @Test
    void testGetClassDescriptor_whereSomeFieldsHaveAccessorsAndOthersDoNot() {

        Map<String, Accessor> accessors = new WriteOptions().getDeepAccessorMap(PrivateFinalObject.class);

        assertThat(accessors).hasSize(4);
        assertThat(accessors.get("x").isPublic()).isFalse();
        assertThat(accessors.get("x").getDisplayName()).isEqualTo("x");
        assertThat(accessors.get("y").isPublic()).isFalse();
        assertThat(accessors.get("y").getDisplayName()).isEqualTo("y");
        assertThat(accessors.get("key").isPublic()).isTrue();
        assertThat(accessors.get("key").getDisplayName()).isEqualTo("getKey");
        assertThat(accessors.get("flatuated").isPublic()).isTrue();
        assertThat(accessors.get("flatuated").getDisplayName()).isEqualTo("isFlatuated");
    }

    @Test
    void testGetClassDescriptor_onEnumDefinitionClass_findsName() {
        Map<String, Accessor> accessors = new WriteOptions().getDeepAccessorMap(ColorEnum.class);
        assertThat(accessors).hasSize(1);
        assertThat(accessors.get("name").isPublic()).isTrue();
        assertThat(accessors.get("name").getDisplayName()).isEqualTo("name");
    }

    @Test
    void testGetClassDescriptor_onEnumClass() {
        Map<String, Accessor> accessors = new WriteOptions().getDeepAccessorMap(Enum.class);

        assertThat(accessors).hasSize(1);
        assertThat(accessors.get("name").isPublic()).isTrue();
        assertThat(accessors.get("name").getDisplayName()).isEqualTo("name");
    }

    @Test
    void testCloningObject_withFieldBlacklistedAtSubclassOfEntity_fieldsAreAvailableOnOtherSubsOfEntity() {
        WriteOptions options = new WriteOptions()
                .addExcludedFields(Car.class, MetaUtils.listOf("id", "updated", "created"));

        Car initial = createCar();

        String json = TestUtil.toJson(initial, options);
        Car car = TestUtil.toObjects(json, null);

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
        WriteOptions options = new WriteOptions()
                .addExcludedFields(Entity.class, MetaUtils.listOf("id", "updated", "created"));

        Car initial = createCar();

        String json = TestUtil.toJson(initial, options);
        Car car = TestUtil.toObjects(json, null);

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
        Car car = create(new Car(), 1L);
        car.setMake("Ford");
        car.setModel("Mustang");
        car.setColor(ColorEnum.RED);
        car.setInstalledParts(createParts());
        car.setAccessories(createAccessories());
        return car;
    }

    private List<Part> createParts() {
        Part tire = create(new Part(), 55L);
        tire.setName("Pirelli");
        tire.setDescription("Pirelli blah blah blah");

        Part steeringWheel = create(new Part(), 99L);
        steeringWheel.setDescription("Yolk style steering wheel");
        steeringWheel.setName("Yolk");

        Part exhaust = create(new Part(), 87L);
        exhaust.setDescription("Brand Dual Exhast");
        exhaust.setName("Brando");

        return MetaUtils.listOf(tire, steeringWheel, exhaust);
    }

    private List<Part> createAccessories() {
        Part tire = create(new Part(), 29L);
        tire.setName("Heated Mirrors");
        tire.setDescription("Heated Mirrors");

        Part steeringWheel = create(new Part(), 31L);
        steeringWheel.setDescription("Heated Seats");
        steeringWheel.setName("Heated Seats");

        Part exhaust = create(new Part(), 22L);
        exhaust.setDescription("Floor Mats");
        exhaust.setName("Floor Mats");

        return MetaUtils.listOf(tire, steeringWheel, exhaust);
    }

    private <T extends Entity> T create(T entity, Long id) {
        entity.setId(id);
        entity.setCreated(new Date());
        entity.setUpdated(entity.getCreated());
        return entity;
    }
}
