package com.cedarsoftware.util.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FastByteArrayOutputStreamTest {

    @Test
    void testDefaultConstructor() {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();
        assertNotNull(outputStream);
        assertEquals(0, outputStream.size());
    }

    @Test
    void testConstructorWithInitialSize() {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream(100);
        assertNotNull(outputStream);
        assertEquals(0, outputStream.size());
    }

    @Test
    void testConstructorWithNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> new FastByteArrayOutputStream(-1));
    }

    @Test
    void testWriteSingleByte() {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();
        outputStream.write(65); // ASCII for 'A'
        assertEquals(1, outputStream.size());
        assertArrayEquals(new byte[]{(byte) 65}, outputStream.toByteArray());
    }

    @Test
    void testWriteByteArrayWithOffsetAndLength() {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();
        byte[] data = "Hello".getBytes();
        outputStream.write(data, 1, 3); // "ell"
        assertEquals(3, outputStream.size());
        assertArrayEquals("ell".getBytes(), outputStream.toByteArray());
    }

    @Test
    void testWriteByteArray() throws IOException {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();
        byte[] data = "Hello World".getBytes();
        outputStream.write(data);
        assertEquals(data.length, outputStream.size());
        assertArrayEquals(data, outputStream.toByteArray());
    }

    @Test
    void testReset() {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();
        outputStream.write(65); // ASCII for 'A'
        outputStream.reset();
        assertEquals(0, outputStream.size());
    }

    @Test
    void testToByteArray() throws IOException {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();
        byte[] data = "Test".getBytes();
        outputStream.write(data);
        assertArrayEquals(data, outputStream.toByteArray());
        assertEquals(data.length, outputStream.size());
    }

    @Test
    void testSize() {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();
        assertEquals(0, outputStream.size());
        outputStream.write(65); // ASCII for 'A'
        assertEquals(1, outputStream.size());
    }

    @Test
    void testToString() throws IOException {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();
        String str = "Hello";
        outputStream.write(str.getBytes());
        assertEquals(str, outputStream.toString());
    }

    @Test
    void testWriteTo() throws IOException {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();
        byte[] data = "Hello World".getBytes();
        outputStream.write(data);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        outputStream.writeTo(baos);

        assertArrayEquals(data, baos.toByteArray());
    }

    @Test
    void testClose() {
        FastByteArrayOutputStream outputStream = new FastByteArrayOutputStream();
        assertDoesNotThrow(outputStream::close);
    }
}

