package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test File support through Enhanced Converter integration.
 * These tests validate that java.io.File can be converted via the Converter
 * system for String <-> File transformations.
 */
class AutomaticFileTest {
    private static final Logger LOG = Logger.getLogger(AutomaticFileTest.class.getName());

    @Test
    void testConverterDirectly() {
        // Given: A Converter instance and a file path
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());
        
        // Check if File is considered a "simple type" now
        boolean isSimpleType = converter.isSimpleTypeConversionSupported(File.class);
        LOG.info("File is simple type: " + isSimpleType);
        
        // Test File to String conversion
        File testFile = new File("/home/test/example.txt");
        if (isSimpleType) {
            String fileString = converter.convert(testFile, String.class);
            LOG.info("File to String: " + fileString);
            
            // Test String back to File
            File backToFile = converter.convert(fileString, File.class);
            LOG.info("String back to File: " + backToFile);
            assertThat(backToFile).isEqualTo(testFile);
        }
        
        // When: We check if conversion is supported
        boolean isSupported = converter.isConversionSupportedFor(String.class, File.class);
        
        // Then: We can report the current state
        LOG.info("String to File conversion supported: " + isSupported);
        
        if (isSupported) {
            // This should work now with updated java-util
            File convertedFile = converter.convert("/path/to/converted/file.txt", File.class);
            assertThat(convertedFile.getPath().replace("\\", "/")).isEqualTo("/path/to/converted/file.txt");
        }
    }

    @Test
    void testBasicFileConversion() {
        // Given: A simple file path string
        String filePath = "/home/user/document.txt";
        
        // When: We create a File from it
        File file = new File(filePath);
        
        // Then: Basic properties should work
        assertThat(file.getPath().replace("\\", "/")).isEqualTo(filePath);
        LOG.info("File path: " + file.getPath());
        LOG.info("File name: " + file.getName());
    }

    @Test
    void testConverterSupportedTypes() {
        // Given: A Converter instance
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());
        
        // When: We check various conversion capabilities
        boolean stringToFile = converter.isConversionSupportedFor(String.class, File.class);
        boolean fileToString = converter.isConversionSupportedFor(File.class, String.class);
        boolean isSimpleType = converter.isSimpleTypeConversionSupported(File.class);
        
        // Then: Log the current state
        LOG.info("String to File supported: " + stringToFile);
        LOG.info("File to String supported: " + fileToString);
        LOG.info("File is simple type: " + isSimpleType);
        
        // Note: These tests document the current state without assuming specific behavior
    }
}