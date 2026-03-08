package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.annotation.IoShowType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IoShowType} annotation.
 * Verifies that @IoShowType forces type emission on annotated fields
 * regardless of the global showTypeInfo setting.
 */
class IoShowTypeTest {

    // ========================================
    // Test domain classes
    // ========================================

    static abstract class Vehicle {
        String make;

        Vehicle() {}

        Vehicle(String make) {
            this.make = make;
        }
    }

    static class Car extends Vehicle {
        int doors;

        Car() {}

        Car(String make, int doors) {
            super(make);
            this.doors = doors;
        }
    }

    static class Truck extends Vehicle {
        int payload;

        Truck() {}

        Truck(String make, int payload) {
            super(make);
            this.payload = payload;
        }
    }

    static class Van extends Vehicle {
        int seats;

        Van() {}

        Van(String make, int seats) {
            super(make);
            this.seats = seats;
        }
    }

    // Container with @IoShowType on various field types
    static class Fleet {
        @IoShowType
        List<Vehicle> vehicles;

        @IoShowType
        Vehicle primary;

        @IoShowType
        Vehicle[] spares;

        @IoShowType
        Map<String, Vehicle> namedVehicles;

        String name;
        int count;

        Fleet() {}
    }

    // Container WITHOUT @IoShowType — for comparison
    static class FleetNoAnnotation {
        List<Vehicle> vehicles;
        Vehicle primary;
        String name;

        FleetNoAnnotation() {}
    }

    // ========================================
    // JSON tests — showTypeInfoNever
    // ========================================

    @Test
    void testPlainFieldWithShowTypeNever() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet fleet = new Fleet();
        fleet.primary = new Car("Toyota", 4);
        fleet.name = "Test Fleet";
        fleet.count = 1;

        String json = JsonIo.toJson(fleet, writeOptions);

        // @IoShowType should force type on the Car value even with showTypeInfoNever
        assertTrue(json.contains("\"@type\""), "@IoShowType should force type emission on plain field, got: " + json);
        assertTrue(json.contains("Car"), "Type should identify Car, got: " + json);
    }

    @Test
    void testListFieldWithShowTypeNever() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet fleet = new Fleet();
        fleet.vehicles = new ArrayList<>();
        fleet.vehicles.add(new Car("Toyota", 4));
        fleet.vehicles.add(new Truck("Ford", 5000));
        fleet.vehicles.add(new Van("Honda", 8));
        fleet.name = "Mixed Fleet";
        fleet.count = 3;

        String json = JsonIo.toJson(fleet, writeOptions);

        // Each element should have @type
        assertTrue(json.contains("Car"), "Should contain Car type, got: " + json);
        assertTrue(json.contains("Truck"), "Should contain Truck type, got: " + json);
        assertTrue(json.contains("Van"), "Should contain Van type, got: " + json);
    }

    @Test
    void testArrayFieldWithShowTypeNever() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet fleet = new Fleet();
        fleet.spares = new Vehicle[]{new Car("BMW", 2), new Truck("Mack", 10000)};
        fleet.name = "Spares Fleet";
        fleet.count = 2;

        String json = JsonIo.toJson(fleet, writeOptions);

        assertTrue(json.contains("Car"), "Should contain Car type, got: " + json);
        assertTrue(json.contains("Truck"), "Should contain Truck type, got: " + json);
    }

    @Test
    void testMapValueFieldWithShowTypeNever() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet fleet = new Fleet();
        fleet.namedVehicles = new LinkedHashMap<>();
        fleet.namedVehicles.put("primary", new Car("Tesla", 4));
        fleet.namedVehicles.put("backup", new Van("Chrysler", 7));
        fleet.name = "Named Fleet";
        fleet.count = 2;

        String json = JsonIo.toJson(fleet, writeOptions);

        assertTrue(json.contains("Car"), "Should contain Car type, got: " + json);
        assertTrue(json.contains("Van"), "Should contain Van type, got: " + json);
    }

    @Test
    void testFieldWithoutAnnotationStillSuppressed() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        FleetNoAnnotation fleet = new FleetNoAnnotation();
        fleet.primary = new Car("Toyota", 4);
        fleet.vehicles = new ArrayList<>();
        fleet.vehicles.add(new Truck("Ford", 5000));
        fleet.name = "No Annotation Fleet";

        String json = JsonIo.toJson(fleet, writeOptions);

        // Without @IoShowType and with showTypeInfoNever, no type info should appear
        // (except possibly for the root object, which we don't test here)
        // The field values should NOT have @type for the Vehicle subclasses
        assertFalse(json.contains("Car"), "Without @IoShowType, Car type should not appear, got: " + json);
        assertFalse(json.contains("Truck"), "Without @IoShowType, Truck type should not appear, got: " + json);
    }

    // ========================================
    // Round-trip tests — verify correct types survive serialization
    // ========================================

    @Test
    void testListRoundTripWithShowTypeNever() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet original = new Fleet();
        original.vehicles = new ArrayList<>();
        original.vehicles.add(new Car("Toyota", 4));
        original.vehicles.add(new Truck("Ford", 5000));
        original.vehicles.add(new Van("Honda", 8));
        original.name = "Round Trip Fleet";
        original.count = 3;

        String json = JsonIo.toJson(original, writeOptions);
        Fleet restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Fleet.class);

        assertEquals(3, restored.vehicles.size());
        assertTrue(restored.vehicles.get(0) instanceof Car, "First vehicle should be Car");
        assertTrue(restored.vehicles.get(1) instanceof Truck, "Second vehicle should be Truck");
        assertTrue(restored.vehicles.get(2) instanceof Van, "Third vehicle should be Van");

        Car car = (Car) restored.vehicles.get(0);
        assertEquals("Toyota", car.make);
        assertEquals(4, car.doors);

        Truck truck = (Truck) restored.vehicles.get(1);
        assertEquals("Ford", truck.make);
        assertEquals(5000, truck.payload);
    }

    @Test
    void testPlainFieldRoundTripWithShowTypeNever() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet original = new Fleet();
        original.primary = new Car("BMW", 2);
        original.name = "Single Vehicle";
        original.count = 1;

        String json = JsonIo.toJson(original, writeOptions);
        Fleet restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Fleet.class);

        assertTrue(restored.primary instanceof Car, "Primary should be Car, got: " + restored.primary.getClass().getName());
        Car car = (Car) restored.primary;
        assertEquals("BMW", car.make);
        assertEquals(2, car.doors);
    }

    @Test
    void testArrayRoundTripWithShowTypeNever() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet original = new Fleet();
        original.spares = new Vehicle[]{new Truck("Mack", 10000), new Van("Mercedes", 12)};
        original.name = "Spares";
        original.count = 2;

        String json = JsonIo.toJson(original, writeOptions);
        Fleet restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Fleet.class);

        assertEquals(2, restored.spares.length);
        assertTrue(restored.spares[0] instanceof Truck);
        assertTrue(restored.spares[1] instanceof Van);
    }

    @Test
    void testMapRoundTripWithShowTypeNever() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet original = new Fleet();
        original.namedVehicles = new LinkedHashMap<>();
        original.namedVehicles.put("main", new Car("Tesla", 4));
        original.namedVehicles.put("hauler", new Truck("Peterbilt", 80000));
        original.name = "Named";
        original.count = 2;

        String json = JsonIo.toJson(original, writeOptions);
        Fleet restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Fleet.class);

        assertTrue(restored.namedVehicles.get("main") instanceof Car);
        assertTrue(restored.namedVehicles.get("hauler") instanceof Truck);
    }

    // ========================================
    // JSON5 mode tests
    // ========================================

    @Test
    void testJson5ModeWithShowType() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .build();

        Fleet fleet = new Fleet();
        fleet.vehicles = new ArrayList<>();
        fleet.vehicles.add(new Car("Toyota", 4));
        fleet.vehicles.add(new Truck("Ford", 5000));
        fleet.name = "JSON5 Fleet";
        fleet.count = 2;

        String json = JsonIo.toJson(fleet, writeOptions);

        // JSON5 defaults to showTypeInfoNever, but @IoShowType should override for annotated fields
        assertTrue(json.contains("Car"), "JSON5 with @IoShowType should emit type for Car, got: " + json);
        assertTrue(json.contains("Truck"), "JSON5 with @IoShowType should emit type for Truck, got: " + json);
    }

    // ========================================
    // TOON format tests
    // ========================================

    @Test
    void testToonWithShowType() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet fleet = new Fleet();
        fleet.vehicles = new ArrayList<>();
        fleet.vehicles.add(new Car("Toyota", 4));
        fleet.vehicles.add(new Truck("Ford", 5000));
        fleet.name = "TOON Fleet";
        fleet.count = 2;

        String toon = JsonIo.toToon(fleet, writeOptions);

        // @IoShowType should force $type on elements even in TOON format
        assertTrue(toon.contains("Car"), "TOON with @IoShowType should emit type for Car, got: " + toon);
        assertTrue(toon.contains("Truck"), "TOON with @IoShowType should emit type for Truck, got: " + toon);
    }

    @Test
    void testToonPlainFieldWithShowType() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet fleet = new Fleet();
        fleet.primary = new Van("Honda", 8);
        fleet.name = "TOON Single";
        fleet.count = 1;

        String toon = JsonIo.toToon(fleet, writeOptions);

        assertTrue(toon.contains("Van"), "TOON with @IoShowType should emit type for Van, got: " + toon);
    }

    @Test
    void testToonRoundTrip() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet original = new Fleet();
        original.vehicles = new ArrayList<>();
        original.vehicles.add(new Car("Toyota", 4));
        original.vehicles.add(new Truck("Ford", 5000));
        original.primary = new Van("Honda", 8);
        original.name = "TOON RT";
        original.count = 3;

        String toon = JsonIo.toToon(original, writeOptions);
        Fleet restored = JsonIo.fromToon(toon, new ReadOptionsBuilder().build()).asClass(Fleet.class);

        assertTrue(restored.primary instanceof Van);
        assertEquals(2, restored.vehicles.size());
        assertTrue(restored.vehicles.get(0) instanceof Car);
        assertTrue(restored.vehicles.get(1) instanceof Truck);
    }

    // ========================================
    // Interaction with other showTypeInfo modes
    // ========================================

    @Test
    void testShowTypeMinimalWithAnnotation() {
        // With MINIMAL, type is already shown when runtime != declared type.
        // @IoShowType should still work (redundantly) without causing issues.
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoMinimal()
                .build();

        Fleet fleet = new Fleet();
        fleet.primary = new Car("Toyota", 4);
        fleet.name = "Minimal Fleet";
        fleet.count = 1;

        String json = JsonIo.toJson(fleet, writeOptions);

        // Should still have type info (MINIMAL would show it anyway for Vehicle→Car mismatch,
        // but @IoShowType ensures it)
        assertTrue(json.contains("Car"), "Type should appear with MINIMAL + @IoShowType, got: " + json);
    }

    @Test
    void testUnannotatedFieldRespectsModeNormally() {
        // Fields WITHOUT @IoShowType should follow the normal showTypeInfo mode
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoMinimal()
                .build();

        FleetNoAnnotation fleet = new FleetNoAnnotation();
        fleet.primary = new Car("Toyota", 4);
        fleet.name = "Test";

        String json = JsonIo.toJson(fleet, writeOptions);

        // With MINIMAL, type IS shown for Vehicle→Car since they differ
        assertTrue(json.contains("Car"), "MINIMAL should show type for polymorphic field");
    }

    // ========================================
    // Primitive fields should NOT be affected
    // ========================================

    @Test
    void testNonAnnotatedPrimitivesUnaffected() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        Fleet fleet = new Fleet();
        fleet.name = "Test";
        fleet.count = 42;

        String json = JsonIo.toJson(fleet, writeOptions);

        // "name" and "count" are not annotated with @IoShowType, and are primitives/Strings
        // They should NOT get @type
        assertFalse(json.contains("\"String\""), "String field should not get type");
        assertFalse(json.contains("\"int\""), "int field should not get type");
    }
}
