package com.cedarsoftware.io.factory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;

/**
 * Use to create new instances of collection interfaces (needed for empty collections)
 */
@Deprecated // Not really, using as marker to indicate it is not yet LOADING, only creating.
public class CollectionFactory implements JsonReader.ClassFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver)
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