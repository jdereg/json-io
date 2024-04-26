package com.cedarsoftware.io.reflect;

import java.util.Date;
import java.util.List;

import com.cedarsoftware.io.TestUtil;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;
import com.cedarsoftware.io.reflect.filters.models.Car;
import com.cedarsoftware.io.reflect.filters.models.ColorEnum;
import com.cedarsoftware.io.reflect.filters.models.Entity;
import com.cedarsoftware.io.reflect.filters.models.Part;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThat;

class ClassDescriptorsTests {
    @Test
    void testCloningObject_withFieldBlacklistedAtSubclassOfEntity_fieldsAreAvailableOnOtherSubsOfEntity() {
        WriteOptions options = new WriteOptionsBuilder()
                .addExcludedFields(Car.class, listOf("id", "updated", "created")).build();

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
        WriteOptions options = new WriteOptionsBuilder()
                .addExcludedFields(Entity.class, listOf("id", "updated", "created")).build();

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

        return listOf(tire, steeringWheel, exhaust);
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

        return listOf(tire, steeringWheel, exhaust);
    }

    private <T extends Entity> T create(T entity, Long id) {
        entity.setId(id);
        entity.setCreated(new Date());
        entity.setUpdated(entity.getCreated());
        return entity;
    }
}
