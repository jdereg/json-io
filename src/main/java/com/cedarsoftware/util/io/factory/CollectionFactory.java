package com.cedarsoftware.util.io.factory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;

/**
 * Use to create new instances of collection interfaces (needed for empty collections)
 */
public class CollectionFactory implements JsonReader.ClassFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context)
    {
        if (List.class.isAssignableFrom(c))
        {
            return new ArrayList<>();
        }
        else if (SortedSet.class.isAssignableFrom(c))
        {
            return new TreeSet<>();
        }
        else if (Set.class.isAssignableFrom(c))
        {
            return new LinkedHashSet<>();
        }
        else if (Collection.class.isAssignableFrom(c))
        {
            return new ArrayList<>();
        }
        throw new JsonIoException("CollectionFactory handed Class for which it was not expecting: " + c.getName());
    }
}