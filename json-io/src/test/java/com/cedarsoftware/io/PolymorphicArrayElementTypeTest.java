package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolymorphicArrayElementTypeTest {

    @Test
    void explicitArrayElementTypeShouldNotBeOverwrittenByDeclaredComponentType() {
        String base = PolymorphicArrayElementTypeTest.class.getName();
        String json = "{\"@type\":\"" + base + "$PetHolder\",\"pets\":[" +
                "{\"@type\":\"" + base + "$Dog\",\"name\":\"Fido\",\"breed\":\"Collie\"}" +
                "]}";

        ReadOptions options = new ReadOptionsBuilder()
                .addNotCustomReaderClass(Pet.class)
                .build();

        PetHolder holder = TestUtil.toJava(json, options).asClass(PetHolder.class);
        assertNotNull(holder.pets);
        assertEquals(1, holder.pets.length);
        assertTrue(holder.pets[0] instanceof Dog);
        Dog dog = (Dog) holder.pets[0];
        assertEquals("Fido", dog.name);
        assertEquals("Collie", dog.breed);
    }

    static class PetHolder {
        public Pet[] pets;
    }

    static class Pet {
        public String name;
    }

    static class Dog extends Pet {
        public String breed;
    }
}
