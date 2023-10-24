package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.CustomWriterTest;
import com.cedarsoftware.util.io.JsonReader.ClassFactory;
import com.cedarsoftware.util.io.CustomWriterTest;

public class PersonFactory implements ClassFactory {
    @Override
    public Object newInstance(Class c, JsonObject o) {
        return new CustomWriterTest.Person();
    }
}
