package com.cedarsoftware.util.reflect.factories;

import com.cedarsoftware.util.reflect.Injector;
import com.cedarsoftware.util.reflect.InjectorFactory;
import com.cedarsoftware.util.reflect.injectors.MethodInjector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;

public class MappedMethodInjectorFactory implements InjectorFactory {
    private static final int METHOD_MODIFIERS = Modifier.PUBLIC | Modifier.STATIC;

    @Override
    public Injector createInjector(Field field, Map<String, Method> possibleInjectors) {
        String fieldName = field.getName();

        Optional<String> possibleMethod = NonStandardInjectorNames.instance()
                .getMapping(field.getDeclaringClass(), fieldName);

        Method method = possibleInjectors.get(possibleMethod.orElse(createGetterName(fieldName)));

        if (method == null || (method.getModifiers() & METHOD_MODIFIERS) != Modifier.PUBLIC) {
            return null;
        }

        return new MethodInjector(field, method);
    }

    /**
     * Creates the common name for a get Method
     *
     * @param fieldName - String representing the field name
     * @return String - returns the appropriate method name to access this fieldName.
     */
    private static String createGetterName(String fieldName) {
        return "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }
}
