package com.cedarsoftware.io.reflect.factories;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.io.reflect.InjectorFactory;

public class MethodInjectorFactory implements InjectorFactory {
    @Override
    public Injector createInjector(Field field, Map<Class<?>, Map<String, String>> nonStandardNames, String uniqueName) {

        final String fieldName = field.getName();
        final Optional<String> possibleMethod = getMapping(nonStandardNames, field.getDeclaringClass(), fieldName);
        final String method = possibleMethod.orElse(createSetterName(fieldName));

        return Injector.create(field, method, uniqueName);
    }

    /**
     * Creates the common name for a get Method
     *
     * @param fieldName - String representing the field name
     * @return String - returns the appropriate method name to access this fieldName.
     */
    private static String createSetterName(String fieldName) {
        return "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }
}
