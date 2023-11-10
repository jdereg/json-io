package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;

import java.time.Instant;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Abstract class to help create temporal items.
 * <p>
 * All custom writers for json-io subclass this class.  Special writers are not needed for handling
 * user-defined classes.  However, special writers are built/supplied by json-io for many of the
 * primitive types and other JDK classes simply to allow for a more concise form.
 * <p>
 *
 * @author Kenny Partlow (kpartlow@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class OffsetTimeFactory extends AbstractTemporalFactory<OffsetTime> {

    public OffsetTimeFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public OffsetTimeFactory() {
        super(DateTimeFormatter.ISO_OFFSET_TIME);
    }

    @Override
    protected OffsetTime fromNumber(Number l) {
        return OffsetTime.from(Instant.ofEpochMilli(l.longValue()));
    }

    @Override
    protected OffsetTime fromString(String s) {
        try {
            return OffsetTime.parse(s, dateTimeFormatter);
        } catch (Exception e) {   // Increase date-time format flexibility - JSON not written by json-io.
            Date date = DateFactory.parseDate(s);
            return date.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toOffsetDateTime().toOffsetTime();
        }
    }

    @Override
    protected OffsetTime fromJsonObject(JsonObject job) {
        return null;
    }
}