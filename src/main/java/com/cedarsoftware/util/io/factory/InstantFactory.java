package com.cedarsoftware.util.io.factory;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import com.cedarsoftware.util.io.ArgumentHelper;
import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

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
 *         limitations under the License.
 */
public class InstantFactory extends AbstractTemporalFactory<Instant> {

    public InstantFactory(DateTimeFormatter dateFormatter, ZoneId zoneId) {
        super(dateFormatter, zoneId);
    }

    public InstantFactory() {
        super(ISO_INSTANT, ZoneId.systemDefault());
    }

    @Override
    protected Instant fromString(String s) {
        try {
            return dateTimeFormatter.parse(s, Instant::from);
        } catch (Exception e) {   // Increase date format flexibility - JSON not written by json-io.
            Date date = DateFactory.parseDate(s);

            if (date == null) {
                throw new JsonIoException("Could not parse date: " + s);
            }

            return date.toInstant();
        }
    }

    @Override
    protected Instant fromJsonObject(JsonObject job, ReaderContext context) {
        Number seconds = ArgumentHelper.getNumberWithDefault(job.get("seconds"), 0);
        Number nanos = ArgumentHelper.getNumberWithDefault(job.get("nanos"), 0);

        return Instant.ofEpochSecond(seconds.longValue(), nanos.longValue());
    }
}
