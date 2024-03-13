package com.cedarsoftware.io.factory;

import com.cedarsoftware.io.CustomWriterTest;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader.ClassFactory;
import com.cedarsoftware.io.ReaderContext;

public class PersonFactory implements ClassFactory {
    @Override
    public Object newInstance(Class c, JsonObject jObj, ReaderContext context) {
        return new CustomWriterTest.Person();
    }
}
