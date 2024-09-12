package com.cedarsoftware.io.reflect.factories;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.io.reflect.InjectorFactory;

public class AllowAllNonStaticFieldMethodInjectorFactory implements InjectorFactory {

    @Override
    public Injector createInjector(final Field field,
                                   final Map<Class<?>, Map<String, String>> nonStandardNames,
                                   final String uniqueName) {
        if (!Modifier.isStatic(field.getModifiers()) && !field.isAccessible()) {
            // it makes Lookup to be changed to trusted during the unreflectField
            field.setAccessible(true);
        }

        return Injector.create(field, uniqueName);
    }
}
