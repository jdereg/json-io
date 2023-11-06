package com.cedarsoftware.util.reflect.accessors;

import com.cedarsoftware.util.io.MetaUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MethodAccessor extends AbstractAccessor {
    private final Method method;

    public MethodAccessor(Field field, Method method) {
        super(field);

        //  TODO:  should we only send back public methods and then allow and revert to FieldAccessor later in the chain to do work like it always ahs.
        // class modifiers for static classes (like in our tests) may not be accessible if class is not public
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
