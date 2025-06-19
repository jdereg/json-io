package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for JsonReader.DefaultReferenceTracker.
 */
class DefaultReferenceTrackerTest {

    @Test
    void sizeReturnsNumberOfTrackedReferences() {
        JsonReader.DefaultReferenceTracker tracker = new JsonReader.DefaultReferenceTracker();

        assertEquals(0, tracker.size());

        tracker.put(1L, new JsonObject());
        assertEquals(1, tracker.size());

        tracker.put(2L, new JsonObject());
        assertEquals(2, tracker.size());

        tracker.clear();
        assertEquals(0, tracker.size());
    }
}
