package com.cedarsoftware.util.io;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.cedarsoftware.util.io.MetaUtilsHelper.classForName;
import static com.cedarsoftware.util.io.MetaUtils.trySetAccessible;

/**
 * Wrapper for unsafe, decouples direct usage of sun.misc.* package.
 * @author Kai Hufenback
 */
final class Unsafe
{
    private final Object sunUnsafe;
    private final Method allocateInstance;

    /**
     * Constructs unsafe object, acting as a wrapper.
     * @throws InvocationTargetException
     */
    public Unsafe() throws InvocationTargetException {
        try {
            Constructor<?> unsafeConstructor = classForName("sun.misc.Unsafe", MetaUtils.class.getClassLoader()).getDeclaredConstructor();
            trySetAccessible(unsafeConstructor);
            sunUnsafe = unsafeConstructor.newInstance();
            allocateInstance = sunUnsafe.getClass().getMethod("allocateInstance", Class.class);
            trySetAccessible(allocateInstance);
        }
        catch (Exception e) {
            throw new JsonIoException("Unable to use sun.misc.Unsafe to construct objects.", e);
        }
    }

    /**
     * Creates an object without invoking constructor or initializing variables.
     * <b>Be careful using this with JDK objects, like URL or ConcurrentHashMap this may bring your VM into troubles.</b>
     * @param clazz to instantiate
     * @return allocated Object
     */
    public Object allocateInstance(Class<?> clazz)
    {
        try {
            return allocateInstance.invoke(sunUnsafe, clazz);
        }
        catch (IllegalAccessException | IllegalArgumentException e ) {
            String name = clazz == null ? "null" : clazz.getName();
            throw new JsonIoException("Unable to create instance of class: " + name, e);
        }
        catch (InvocationTargetException e) {
            String name = clazz == null ? "null" : clazz.getName();
            throw new JsonIoException("Unable to create instance of class: " + name, e.getCause() != null ? e.getCause() : e);
        }
    }
}
