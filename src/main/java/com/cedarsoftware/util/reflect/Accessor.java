package com.cedarsoftware.util.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

import com.cedarsoftware.util.io.MetaUtils;
import lombok.Getter;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.*
 */
public class Accessor extends FieldCharacteristics {

    @Getter
    private final String displayName;
    private final MethodHandle accessor;

    @Getter
    private final boolean isPublic;

    public Accessor(Field field) throws Throwable {
        super(field);

        this.displayName = field.getName();
        this.isPublic = Modifier.isPublic(field.getModifiers());

        if (!(Modifier.isPublic(field.getModifiers()) && Modifier.isPublic(field.getDeclaringClass().getModifiers()))) {
            MetaUtils.trySetAccessible(field);
        }

        this.accessor = MethodHandles.lookup().unreflectGetter(field);
    }

    public Accessor(Field field, Method method) throws Throwable {
        super(field);

        this.displayName = method.getName();
        this.isPublic = Modifier.isPublic(method.getModifiers());

        //  if the declaring class is not accessible we won't have access to it.
        if (!Modifier.isPublic(field.getDeclaringClass().getModifiers())) {
            MetaUtils.trySetAccessible(method);
        }

        this.accessor = MethodHandles.lookup().unreflect(method);
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o.getClass() == getClass()) {
            Accessor other = (Accessor) o;
            return (field.equals(other.field)) &&
                    displayName.equals(other.displayName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, field);
    }


    public Object retrieve(Object o) throws Throwable {
        return accessor.invoke(o);
    }
}
