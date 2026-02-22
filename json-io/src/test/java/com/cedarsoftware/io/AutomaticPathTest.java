package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test Path support through Enhanced Converter integration.
 * These tests validate that java.nio.file.Path can be converted via the Converter
 * system for String <-> Path transformations.
 */
class AutomaticPathTest {

    @Test
    void testConverterDirectly() {
        // Given: A Converter instance and a path
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());

        // Path should be considered a "simple type"
        assertThat(Resolver.isPseudoPrimitive(Path.class)).isTrue();

        // Test Path to String round-trip conversion
        Path testPath = Paths.get("/home/test/example.txt");
        String pathString = converter.convert(testPath, String.class);
        assertThat(pathString).isNotEmpty();

        Path backToPath = converter.convert(pathString, Path.class);
        assertThat(backToPath).isEqualTo(testPath);

        // Verify String to Path conversion is supported
        assertThat(converter.isConversionSupportedFor(String.class, Path.class)).isTrue();

        Path convertedPath = converter.convert("/path/to/converted/file.txt", Path.class);
        assertThat(convertedPath.toString().replace("\\", "/")).isEqualTo("/path/to/converted/file.txt");
    }

    @Test
    void testBasicPathConversion() {
        // Given: A simple path string
        String pathString = "/home/user/document.txt";

        // When: We create a Path from it
        Path path = Paths.get(pathString);

        // Then: Basic properties should work
        assertThat(path.toString().replace("\\", "/")).isEqualTo(pathString);
        assertThat(path.getFileName().toString()).isEqualTo("document.txt");
    }

    @Test
    void testConverterSupportedTypes() {
        // Given: A Converter instance
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());

        // Then: Path conversions should be supported
        assertThat(converter.isConversionSupportedFor(String.class, Path.class)).isTrue();
        assertThat(converter.isConversionSupportedFor(Path.class, String.class)).isTrue();
        assertThat(Resolver.isPseudoPrimitive(Path.class)).isTrue();
    }

    @Test
    void testPathEquality() {
        // Given: Two paths with same string representation
        Path path1 = Paths.get("/home/user/same-file.txt");
        Path path2 = Paths.get("/home/user/same-file.txt");

        // Then: They should be equal
        assertThat(path1).isEqualTo(path2);
        assertThat(path1.toString()).isEqualTo(path2.toString());
    }

    @Test
    void testRelativePath() {
        // Given: A relative path
        Path relativePath = Paths.get("./src/test/resources/data.json");

        // Then: Properties should work correctly
        assertThat(relativePath.toString().replace("\\", "/")).isEqualTo("./src/test/resources/data.json");
        assertThat(relativePath.isAbsolute()).isFalse();
    }
}
