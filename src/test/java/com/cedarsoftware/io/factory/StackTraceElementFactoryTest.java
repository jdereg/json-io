package com.cedarsoftware.io.factory;

import java.util.stream.Stream;

import com.cedarsoftware.io.JsonObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class StackTraceElementFactoryTest {

    private static Stream<Arguments> variants() {
        return Stream.of(
                Arguments.of("app", null, null, "declaringClass", "methodName", "fileName", 239L),
                Arguments.of(null, "module", "version", "declaringClass", "methodName", "fileName", 239L),
                Arguments.of(null, null, null, "declaringClass", "methodName", "fileName", -1L)
        );
    }

    @ParameterizedTest
    @MethodSource("variants")
    void newInstance_testVariants(String classLoaderName, String moduleName, String moduleVersion, String declaringClass, String methodName, String fileName, Long lineNumber) {
        StackTraceElementFactory factory = new StackTraceElementFactory();
        JsonObject jsonObject = buildJsonObject(classLoaderName, moduleName, moduleVersion, declaringClass, methodName, fileName, lineNumber);

        StackTraceElement stackTrace = (StackTraceElement) factory.newInstance(StackTraceElement.class, jsonObject, null);

        assertThat(stackTrace.getClassName()).isEqualTo(declaringClass);
        assertThat(stackTrace.getMethodName()).isEqualTo(methodName);
        assertThat(stackTrace.getFileName()).isEqualTo(fileName);
        assertThat(stackTrace.getLineNumber()).isEqualTo(lineNumber.intValue());
        // Java 9+ properties
        //assertThat(stackTrace.getModuleName()).isEqualTo(moduleName);
        //assertThat(stackTrace.getModuleVersion()).isEqualTo(moduleVersion);
        //assertThat(stackTrace.getClassLoaderName()).isEqualTo(classLoaderName);
    }

    private JsonObject buildJsonObject(String classLoaderName, String moduleName, String moduleVersion, String declaringClass, String methodName, String fileName, Long lineNumber) {
        JsonObject object = new JsonObject();

        object.put("declaringClass", declaringClass);
        object.put("methodName", methodName);
        object.put("fileName", fileName);
        object.put("lineNumber", lineNumber);
        object.put("classLoaderName", classLoaderName);
        object.put("moduleName", moduleName);
        object.put("moduleVersion", moduleVersion);
        return object;
    }
}
