package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RecordDeserializationTest {
    public static class TestPojo {
        public int number;
        public String string;
        public RecordPojo subPojo;

        public TestPojo(int number, String string, RecordPojo subPojo) {
            this.number = number;
            this.string = string;
            this.subPojo = subPojo;
        }
    }

    public static record RecordPojo(String subString, int iSubNumber) {}

    @Test
    public void testRecordRoundTrip() {
        TestPojo testPojo = new TestPojo(3, "myString", new RecordPojo("subString", 42));
        String json = JsonIo.toJson(testPojo, null);
        TestPojo clone = JsonIo.toJava(json, null).asClass(TestPojo.class);

        assertEquals(3, clone.number);
        assertEquals("myString", clone.string);
        assertEquals("subString", clone.subPojo.subString());
        assertEquals(42, clone.subPojo.iSubNumber());
    }
}

