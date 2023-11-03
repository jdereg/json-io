package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

import java.util.List;

/**
 * Factory class to create Throwable instances.  Needed for JDK17+ as the only way to set the
 * 'detailMessage' field on a Throwable is via its constructor.
 * <p>
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class ThrowableFactory implements JsonReader.ClassFactory
{
    public Object newInstance(Class<?> c, JsonObject jsonObj)
    {
        String msg = (String)jsonObj.get("detailMessage");
        JsonObject jObjCause = (JsonObject)jsonObj.get("cause");
        Throwable cause = null;
        if (jObjCause != null)
        {
            JsonReader jr = new JsonReader();
            cause = (Throwable) jr.jsonObjectsToJava(jObjCause);
        }

        Throwable t = createException(msg, cause);
        return t;
    }

    protected Throwable createException(String msg, Throwable cause)
    {
        return new Throwable(msg, cause);
    }

    public boolean isObjectFinal()
    {
        return true;
    }
}
