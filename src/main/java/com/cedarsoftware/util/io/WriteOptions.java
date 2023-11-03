package com.cedarsoftware.util.io;

import com.cedarsoftware.util.reflect.Accessor;

import java.util.Collection;
import java.util.Map;

public interface WriteOptions {

    Map<String, String> getCustomTypeMap();

    boolean isUsingShortMetaKeys();

    boolean isAlwaysShowingType();

    boolean isNeverShowingType();

    boolean isPrettyPrint();

    boolean isWritingLongsAsStrings();

    boolean isSkippingNullFields();

    boolean isForcingMapFormatWithKeyArrays();

    boolean isEnumPublicOnly();

    JsonWriter.JsonClassWriter getEnumWriter();

    String getDateFormat();

    Map<Class<?>, JsonWriter.JsonClassWriter> getCustomWriters();

    ClassLoader getClassLoader();

    Collection<Class<?>> getNonCustomClasses();


    Map<Class<?>, Collection<Accessor>> getFieldSpecifiers();

    Map<Class<?>, Collection<Accessor>> getFieldNameBlackList();

    Object getCustomArgument(String name);
}
