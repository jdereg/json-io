package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test File support through Enhanced Converter integration.
 * These tests validate that java.io.File can be converted via the Converter
 * system for String <-> File transformations.
 */
class AutomaticFileTest {

    @Test
    void testConverterDirectly() {
        // Given: A Converter instance and a file path
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());

        // File should be considered a "simple type"
        assertThat(Resolver.isPseudoPrimitive(File.class)).isTrue();

        // Test File to String round-trip conversion
        File testFile = new File("/home/test/example.txt");
        String fileString = converter.convert(testFile, String.class);
        assertThat(fileString).isNotEmpty();

        File backToFile = converter.convert(fileString, File.class);
        assertThat(backToFile).isEqualTo(testFile);

        // Verify String to File conversion is supported
        assertThat(converter.isConversionSupportedFor(String.class, File.class)).isTrue();

        File convertedFile = converter.convert("/path/to/converted/file.txt", File.class);
        assertThat(convertedFile.getPath().replace("\\", "/")).isEqualTo("/path/to/converted/file.txt");
    }

    @Test
    void testBasicFileConversion() {
        // Given: A simple file path string
        String filePath = "/home/user/document.txt";

        // When: We create a File from it
        File file = new File(filePath);

        // Then: Basic properties should work
        assertThat(file.getPath().replace("\\", "/")).isEqualTo(filePath);
        assertThat(file.getName()).isEqualTo("document.txt");
    }

    @Test
    void testConverterSupportedTypes() {
        // Given: A Converter instance
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());

        // Then: File conversions should be supported
        assertThat(converter.isConversionSupportedFor(String.class, File.class)).isTrue();
        assertThat(converter.isConversionSupportedFor(File.class, String.class)).isTrue();
        assertThat(Resolver.isPseudoPrimitive(File.class)).isTrue();
    }
}
