package com.cedarsoftware.util.reflect.accessors;

import com.cedarsoftware.util.io.MetaUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MethodAccessor extends AbstractAccessor {
    private final Method method;

    private final Object[] EMPTY_ARGS = new Object[]{};

    public MethodAccessor(Field field, Method method) {
        super(field);

        if (!Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            MetaUtils.trySetAccessible(method);
        }

        this.method = method;
    }

    @Override
    public Object retrieve(Object o) throws IllegalAccessException, InvocationTargetException {
        return method.invoke(o);
    }

    @Override
    public boolean isPublic() {
        return super.isPublic() || Modifier.isPublic(method.getModifiers());
    }

}
