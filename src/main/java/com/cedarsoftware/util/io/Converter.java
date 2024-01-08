package com.cedarsoftware.util.io;

import com.cedarsoftware.util.convert.ConverterOptions;
import com.cedarsoftware.util.convert.DefaultConverterOptions;

/**
 * Instance conversion utility.  Convert from primitive to other primitives, plus support for Number, Date,
 * TimeStamp, SQL Date, LocalDate, LocalDateTime, ZonedDateTime, Calendar, Big*, Atomic*, Class, UUID,
 * String, ...<br/>
 * <br/>
 * Converter.convert(value, class) if null passed in, null is returned for most types, which allows "tri-state"
 * Boolean, for example, however, for primitive types, it chooses zero for the numeric ones, `false` for boolean,
 * and 0 for char.<br/>
 * <br/>
 * A Map can be converted to almost all data types.  For some, like UUID, it is expected for the Map to have
 * certain keys ("mostSigBits", "leastSigBits").  For the older Java Date/Time related classes, it expects
 * "time" or "nanos", and for all others, a Map as the source, the "value" key will be used to source the value
 * for the conversion.<br/>
 * <br/>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public final class Converter {

    private static final ConverterOptions defaults = new DefaultConverterOptions();
    private static final com.cedarsoftware.util.convert.Converter instance =
            new com.cedarsoftware.util.convert.Converter(defaults);

    @SuppressWarnings("unchecked")
    /**
     * Uses the default configuration options for you system.
     */
    public static <T> T convert(Object fromInstance, Class<T> toType) {
        return instance.convert(fromInstance, toType);
    }

    /**
     * Allows you to specify (each call) a different conversion options.  Useful so you don't have
     * to recreate the instance of Converter that is out there for every configuration option.  Just
     * provide a different set of CovnerterOptions on the call itself.
     */
    public static <T> T convert(Object fromInstance, Class<T> toType, ConverterOptions options) {
        return instance.convert(fromInstance, toType, options);
    }
}
