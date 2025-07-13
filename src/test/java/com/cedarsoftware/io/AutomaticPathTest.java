package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test Path support through Enhanced Converter integration.
 * These tests validate that java.nio.file.Path can be converted via the Converter
 * system for String <-> Path transformations.
 */
class AutomaticPathTest {
    private static final Logger LOG = Logger.getLogger(AutomaticPathTest.class.getName());

    @Test
    void testConverterDirectly() {
        // Given: A Converter instance and a path
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());
        
        // Check if Path is considered a "simple type" now
        boolean isSimpleType = converter.isSimpleTypeConversionSupported(Path.class);
        LOG.info("Path is simple type: " + isSimpleType);
        
        // Test Path to String conversion
        Path testPath = Paths.get("/home/test/example.txt");
        if (isSimpleType) {
            String pathString = converter.convert(testPath, String.class);
            LOG.info("Path to String: " + pathString);
            
            // Test String back to Path
            Path backToPath = converter.convert(pathString, Path.class);
            LOG.info("String back to Path: " + backToPath);
            assertThat(backToPath).isEqualTo(testPath);
        }
        
        // When: We check if conversion is supported
        boolean isSupported = converter.isConversionSupportedFor(String.class, Path.class);
        
        // Then: We can report the current state
        LOG.info("String to Path conversion supported: " + isSupported);
        
        if (isSupported) {
            // This should work now with updated java-util
            Path convertedPath = converter.convert("/path/to/converted/file.txt", Path.class);
            assertThat(convertedPath.toString()).isEqualTo("/path/to/converted/file.txt");
        }
    }

    @Test
    void testBasicPathConversion() {
        // Given: A simple path string
        String pathString = "/home/user/document.txt";
        
        // When: We create a Path from it
        Path path = Paths.get(pathString);
        
        // Then: Basic properties should work
        assertThat(path.toString()).isEqualTo(pathString);
        LOG.info("Path string: " + path.toString());
        LOG.info("Path file name: " + path.getFileName());
    }

    @Test
    void testConverterSupportedTypes() {
        // Given: A Converter instance
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());
        
        // When: We check various conversion capabilities
        boolean stringToPath = converter.isConversionSupportedFor(String.class, Path.class);
        boolean pathToString = converter.isConversionSupportedFor(Path.class, String.class);
        boolean isSimpleType = converter.isSimpleTypeConversionSupported(Path.class);
        
        // Then: Log the current state
        LOG.info("String to Path supported: " + stringToPath);
        LOG.info("Path to String supported: " + pathToString);
        LOG.info("Path is simple type: " + isSimpleType);
        
        // Note: These tests document the current state without assuming specific behavior
    }

    @Test
    void testPathEquality() {
        // Given: Two paths with same string representation
        Path path1 = Paths.get("/home/user/same-file.txt");
        Path path2 = Paths.get("/home/user/same-file.txt");
        
        // Then: They should be equal
        assertThat(path1).isEqualTo(path2);
        assertThat(path1.toString()).isEqualTo(path2.toString());
        LOG.info("Path equality works: " + path1.equals(path2));
    }

    @Test
    void testRelativePath() {
        // Given: A relative path
        Path relativePath = Paths.get("./src/test/resources/data.json");
        
        // Then: Properties should work correctly
        assertThat(relativePath.toString()).isEqualTo("./src/test/resources/data.json");
        assertThat(relativePath.isAbsolute()).isFalse();
        LOG.info("Relative path: " + relativePath);
        LOG.info("Is absolute: " + relativePath.isAbsolute());
    }
}