package com.cedarsoftware.util.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteOptionsTest {

    private WriteOptions options;

    @BeforeEach
    void setUp() {
        options = new WriteOptions();
    }

    @Test
    void testCopyIfNeeded() {
        WriteOptions writeOptions1 = WriteOptions.copyIfNeeded(null);
        assertNotNull(writeOptions1);

        writeOptions1.shortMetaKeys(true);
        WriteOptions writeOptions2 = WriteOptions.copyIfNeeded(writeOptions1);
        assertSame(writeOptions1, writeOptions2);

        writeOptions1.build();
        writeOptions2 = WriteOptions.copyIfNeeded(writeOptions1);
        assertNotSame(writeOptions1, writeOptions2);
    }

    @Test
    void testDefaultSkipNullFields() {
        assertFalse(options.isSkipNullFields());
    }

    // Example test for a setter method (skipNullFields)
    @Test
    void testSetSkipNullFields() {
        options.skipNullFields(true);
        assertTrue(options.isSkipNullFields());
        options.skipNullFields(false);
        assertFalse(options.isSkipNullFields());
    }

    @Test
    void testClassLoader() {
        options.classLoader(ClassLoader.getSystemClassLoader());
        assertSame(options.getClassLoader(), ClassLoader.getSystemClassLoader());
        options.classLoader(WriteOptionsTest.class.getClassLoader());
        assertSame(options.getClassLoader(), WriteOptionsTest.class.getClassLoader());
    }
    
    // Test for forceMapOutputAsTwoArrays method
    @Test
    void testForceMapOutputAsTwoArrays() {
        options.forceMapOutputAsTwoArrays(true);
        assertTrue(options.isForceMapOutputAsTwoArrays());
    }

    // Test for isLogicalPrimitive method
    @Test
    void testIsLogicalPrimitive() {
        assertTrue(options.isLogicalPrimitive(Date.class)); // Assuming Date is a logical primitive
        assertFalse(options.isLogicalPrimitive(Object.class)); // Assuming Object is not
    }

    // Test for getLogicalPrimitives method
    @Test
    void testGetLogicalPrimitives() {
        Collection<Class<?>> logicalPrimitives = options.getLogicalPrimitives();
        assertNotNull(logicalPrimitives);
        assertTrue(logicalPrimitives instanceof LinkedHashSet);
    }

    // Test for addLogicalPrimitive method
    @Test
    void testAddLogicalPrimitive() {
        options.addLogicalPrimitive(String.class);
        assertTrue(options.isLogicalPrimitive(String.class));
    }

    // Test for sealing the options
    @Test
    void testSeal() {
        assertFalse(options.isSealed());
        options.build();
        assertTrue(options.isSealed());
    }

    // Test for mutating methods after sealing
    @Test
    void testModificationAfterSealing() {
        options.build();
        assertThrows(JsonIoException.class, () -> options.skipNullFields(true));
        // Repeat for other setters
    }

    // Additional tests forthcoming, ahead of pushing to orgin/master.
}
