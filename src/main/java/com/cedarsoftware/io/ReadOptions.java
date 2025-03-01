package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.util.Map;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.convert.ConverterOptions;

/**
 * This class contains all the "feature" control (options) for controlling json-io's
 * flexibility in reading JSON. An instance of this class is passed to the JsonReader.toJson() APIs
 * to set the desired features.
 * <br/><br/>
 * You can make this class immutable and then store the class for re-use.
 * Call the ".build()" method and then no longer can any methods that change state be
 * called - it will throw a JsonIoException.
 * <br/><br/>
 * This class can be created from another ReadOptions instance, using the "copy constructor"
 * that takes a ReadOptions. All properties of the other ReadOptions will be copied to the
 * new instance, except for the 'built' property. That always starts off as false (mutable)
 * so that you can make changes to options.
 * <br/><br/>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
 *         limitations under the License.
 */
public interface ReadOptions {

    enum ReturnType {
        JSON_OBJECTS,
        JAVA_OBJECTS
    }

    enum Decimals {
        DOUBLE,
        BIG_DECIMAL,
        BOTH
    }

    enum Integers {
        LONG,
        BIG_INTEGER,
        BOTH
    }

    /**
     * @return true if floating point values should always be returned as Doubles.  This is the default.
     */
    boolean isFloatingPointDouble();

    /**
     * @return true if floating point values should always be returned as BigDecimals.
     */
    boolean isFloatingPointBigDecimal();

    /**
     * @return true if floating point values should always be returned dynamically, as Double or BigDecimal, favoring
     * Double except when precision would be lost, then BigDecimal is returned.
     */
    boolean isFloatingPointBoth();

    /**
     * @return true if integer values should always be returned as Longs.  This is the default.
     */
    boolean isIntegerTypeLong();

    /**
     * @return true if integer values should always be returned as BigIntegers.
     */
    boolean isIntegerTypeBigInteger();

    /**
     * @return true if integer values should always be returned dynamically, as Long or BigInteger, favoring
     * Long except when precision would be lost, then BigInteger is returned.
     */
    boolean isIntegerTypeBoth();

    boolean isAllowNanAndInfinity();

    /**
     * @return ClassLoader to be used when reading JSON to resolve String named classes.
     */
    ClassLoader getClassLoader();

    /**
     * @return boolean true if an 'unknownTypeClass' is set, false if it is not sell (null).
     */
    boolean isFailOnUnknownType();

    /**
     * @return the Class which will have unknown fields set upon it.  Typically this is a Map derivative.
     */
    Class<?> getUnknownTypeClass();

    /**
     * @return boolean 'true' if the InputStream should be closed when the reading is finished.  The default is 'true.'
     */
    boolean isCloseStream();

    /**
     * @return int maximum level the JSON can be nested.  Once the parsing nesting level reaches this depth, a
     * JsonIoException will be thrown instead of a StackOverflowException.  Prevents security risk from StackOverflow
     * attack vectors.
     */
    int getMaxDepth();

    /**
     * @return int size of LRU Cache used to cache Class to Field and Class to injectors
     */
    int getLruSize();

    /**
     * Alias Type Names, e.g. "ArrayList" instead of "java.util.ArrayList".
     * @param typeName String name of type to fetch alias for.  There are no default aliases.
     * @return String alias name or null if type name is not aliased.
     */
    String getTypeNameAlias(String typeName);

    /**
     * @return boolean true if the passed in Class is being coerced to another type, false otherwise.
     */
    boolean isClassCoerced(Class<?> clazz);

    /**
     * Fetch the coerced class for the passed in fully qualified class name.
     * @param c Class to coerce
     * @return Class destination (coerced) class or null if there is none.
     */
    Class<?> getCoercedClass(Class<?> c);

    /**
     * @return JsonReader.MissingFieldHandler to be called when a field in the JSON is read in, yet there is no
     * corresponding field on the destination object to receive the field value.
     */
    JsonReader.MissingFieldHandler getMissingFieldHandler();

    /**
     * @param clazz Class to check to see if it is non-referenceable.  Non-referenceable classes will always create
     *              a new instance when read in and never use @id/@ref. This uses more memory when the JSON is read in,
     *              as there will be a separate instance in memory for each occurrence. There are certain classes that
     *              json-io automatically treats as non-referenceable, like Strings, Enums, Class, and any Number
     *              instance (BigDecimal, AtomicLong, etc.)  You can add to this list. Often, non-referenceable classes
     *              are useful for classes that can be defined in one line as a JSON, like a LocalDateTime, for example.
     * @return boolean true if the passed in class is considered a non-referenceable class.
     */
    boolean isNonReferenceableClass(Class<?> clazz);

    /**
     * @param clazz Class to see if it is on the not-customized list.  Classes are added to this list when
     *              a class is being picked up through inheritance, and you don't want it to have a custom
     *              reader associated to it.
     * @return boolean true if the passed in class is on the not-customized list, false otherwise.
     */
    boolean isNotCustomReaderClass(Class<?> clazz);

    /**
     * @param clazz Class to check to see if there is a custom reader associated to it.
     * @return boolean true if there is an associated custom reader class associated to the passed in class,
     * false otherwise.
     */
    boolean isCustomReaderClass(Class<?> clazz);

    /**
     * Get the ClassFactory associated to the passed in class.
     * @param c Class for which to fetch the ClassFactory.
     * @return JsonReader.ClassFactory instance associated to the passed in class.
     */
    JsonReader.ClassFactory getClassFactory(Class<?> c);

    /**
     * Fetch the custom reader for the passed in Class.  If it is cached (already associated to the
     * passed in Class), return the same instance, otherwise, make a call to get the custom reader
     * and store that result.
     * @param c Class of object for which fetch a custom reader
     * @return JsonClassReader for the custom class (if one exists), null otherwise.
     */
    JsonReader.JsonClassReader getCustomReader(Class<?> c);

    /**
     * @return true if returning items in basic JSON object format
     */
    boolean isReturningJsonObjects();

    /**
     * @return true if returning items in full Java object formats.  Useful for accurate reproduction of graphs
     * into the orginal types such as when cloning objects.
     */
    boolean isReturningJavaObjects();

    Map<String, Injector> getDeepInjectorMap(Class<?> classToTraverse);

    void clearCaches();

    /**
     * Gets the declared fields for the full class hierarchy of a given class
     *
     * @param c - given class.
     * @return Map - map of string fieldName to Field Object.  This will have the
     * deep list of fields for a given class.
     */
    Map<String, Field> getDeepDeclaredFields(final Class<?> c);

    ConverterOptions getConverterOptions();

    /**
     * Get a custom option
     * @param key String name of the custom option
     * @return Object value of the custom option
     */
    Object getCustomOption(String key);
}
