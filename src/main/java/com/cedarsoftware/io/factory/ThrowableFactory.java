package com.cedarsoftware.io.factory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.LoggingConfig;

import static com.cedarsoftware.util.CollectionUtilities.setOf;

/**
 * Factory class to create Throwable instances.
 * 
 * <p>
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
public class ThrowableFactory implements JsonReader.ClassFactory
{
    private static final Logger LOG = Logger.getLogger(ThrowableFactory.class.getName());
    static { LoggingConfig.init(); }

    private static final String DETAIL_MESSAGE = "detailMessage";
    private static final String CAUSE = "cause";
    private static final String STACK_TRACE = "stackTrace";

    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver)
    {
        LOG.log(Level.FINER, "Creating instance of {0} using ThrowableFactory", c.getName());

        // Create a new LinkedHashMap with all fields from JsonObject
        Map<String, Object> namedArguments = new LinkedHashMap<>();

        // Copy all fields from JsonObject, resolving any nested JsonObjects
        for (Map.Entry<Object, Object> entry : jObj.entrySet()) {
            String key = (String) entry.getKey();

            // Skip stackTrace - it's handled separately
            if (STACK_TRACE.equals(key)) {
                continue;
            }

            Object value = entry.getValue();

            // Resolve JsonObjects to Java objects
            if (value instanceof JsonObject) {
                JsonObject jsonValue = (JsonObject) value;
                Class<?> valueType = jsonValue.getRawType();

                // Special handling for known fields with expected types
                if (CAUSE.equals(key) && valueType == null) {
                    valueType = Throwable.class;  // Default type for cause
                }

                // Only resolve if we have a type
                if (valueType != null) {
                    value = resolver.toJavaObjects(jsonValue, valueType);
                }
                // If still no type, leave as JsonObject and let the system handle it later
            }

            namedArguments.put(key, value);
        }

        // Add aliases for known mismatches
        addAliasesForKnownMismatches(namedArguments);

        // First try: parameter name matching WITH aliases
        try {
            // This will use parameter name matching if possible
            Throwable t = (Throwable) ClassUtilities.newInstance(resolver.getConverter(), c, namedArguments);

            // Handle stack trace
            handleStackTrace(t, jObj, resolver);
            return t;
        } catch (Exception parameterMatchException) {
            // Parameter matching failed, build ordered argument list for fallback
            LOG.log(Level.FINE, "Parameter matching failed for {0}, using ordered fallback", c.getName());

            List<Object> arguments = new ArrayList<>();

            // Get message, convert null to empty string (ORIGINAL BEHAVIOR)
            String message = (String) jObj.get(DETAIL_MESSAGE);
            message = (message == null) ? "" : message;
            arguments.add(message);  // Always add message first

            // Handle cause if present
            JsonObject jsonCause = (JsonObject) jObj.get(CAUSE);
            if (jsonCause != null) {
                Class<?> causeType = jsonCause.getRawType();
                if (causeType == null) {
                    causeType = Throwable.class;  // Default to Throwable if no type specified
                }
                Throwable cause = resolver.toJavaObjects(jsonCause, causeType);
                arguments.add(cause);
            }

            // Gather remaining values in order (excluding our known fields)
            gatherRemainingValues(resolver, jObj, arguments, setOf(DETAIL_MESSAGE, CAUSE, STACK_TRACE));

            // Try with the properly ordered collection
            Throwable t = (Throwable) ClassUtilities.newInstance(resolver.getConverter(), c, (Object)arguments);

            // Handle cause if constructor didn't set it
            if (t.getCause() == null && jsonCause != null) {
                Class<?> causeType = jsonCause.getRawType();
                if (causeType == null) {
                    causeType = Throwable.class;
                }
                Throwable cause = resolver.toJavaObjects(jsonCause, causeType);
                t.initCause(cause);
            }

            // Handle stack trace
            handleStackTrace(t, jObj, resolver);
            return t;
        }
    }
    
    private void handleStackTrace(Throwable t, JsonObject jObj, Resolver resolver) {
        Object[] stackTrace = (Object[]) jObj.get(STACK_TRACE);
        if (stackTrace != null) {
            StackTraceElement[] elements = new StackTraceElement[stackTrace.length];
            for (int i = 0; i < stackTrace.length; i++) {
                JsonObject stackTraceMap = (JsonObject) stackTrace[i];
                elements[i] = stackTraceMap == null ? null : resolver.toJavaObjects(stackTraceMap, StackTraceElement.class);
            }
            t.setStackTrace(elements);
        }
    }

    private void addAliasesForKnownMismatches(Map<String, Object> namedArguments) {
        LOG.log(Level.FINEST, "Adding parameter aliases for exception fields");

        // Convert null messages to empty string to match original behavior
        String[] messageFields = {DETAIL_MESSAGE, "message", "msg"};
        for (String field : messageFields) {
            if (namedArguments.containsKey(field) && namedArguments.get(field) == null) {
                namedArguments.put(field, "");
                LOG.log(Level.FINEST, "Converted null {0} to empty string", field);
            }
        }

        // Map detailMessage/message to msg since many constructors use 'msg' as parameter name
        if (!namedArguments.containsKey("msg")) {
            Object messageValue = null;
            if (namedArguments.containsKey(DETAIL_MESSAGE)) {
                messageValue = namedArguments.get(DETAIL_MESSAGE);
            } else if (namedArguments.containsKey("message")) {
                messageValue = namedArguments.get("message");
            } else if (namedArguments.containsKey("reason")) {
                messageValue = namedArguments.get("reason");
            } else if (namedArguments.containsKey("description")) {
                messageValue = namedArguments.get("description");
            } else if (namedArguments.containsKey("error")) {
                messageValue = namedArguments.get("error");
            } else if (namedArguments.containsKey("text")) {
                messageValue = namedArguments.get("text");
            }

            if (messageValue != null) {
                namedArguments.put("msg", messageValue);
                LOG.log(Level.FINEST, "Added alias msg={0}", messageValue);
            }
        }

        // Also ensure message exists if we have detailMessage or other variants
        if (!namedArguments.containsKey("message")) {
            Object messageValue = null;
            if (namedArguments.containsKey(DETAIL_MESSAGE)) {
                messageValue = namedArguments.get(DETAIL_MESSAGE);
            } else if (namedArguments.containsKey("msg")) {
                messageValue = namedArguments.get("msg");
            } else if (namedArguments.containsKey("reason")) {
                messageValue = namedArguments.get("reason");
            }

            if (messageValue != null) {
                namedArguments.put("message", messageValue);
                LOG.log(Level.FINEST, "Added alias message={0}", messageValue);
            }
        }

        // NEW: For constructors that use 's' for string message
        if (!namedArguments.containsKey("s")) {
            Object messageValue = null;
            if (namedArguments.containsKey("message")) {
                messageValue = namedArguments.get("message");
            } else if (namedArguments.containsKey("msg")) {
                messageValue = namedArguments.get("msg");
            } else if (namedArguments.containsKey(DETAIL_MESSAGE)) {
                messageValue = namedArguments.get(DETAIL_MESSAGE);
            }

            if (messageValue != null) {
                namedArguments.put("s", messageValue);
                LOG.log(Level.FINEST, "Added alias s={0}", messageValue);
            }
        }

        // Handle cause aliases
        if (!namedArguments.containsKey("cause") && namedArguments.containsKey("rootCause")) {
            namedArguments.put("cause", namedArguments.get("rootCause"));
            LOG.log(Level.FINEST, "Added alias cause from rootCause");
        }

        if (!namedArguments.containsKey("throwable") && namedArguments.containsKey("cause")) {
            namedArguments.put("throwable", namedArguments.get("cause"));
            LOG.log(Level.FINEST, "Added alias throwable from cause");
        }

        // NEW: For constructors that use 't' for throwable
        if (!namedArguments.containsKey("t")) {
            Object causeValue = null;
            if (namedArguments.containsKey("cause")) {
                causeValue = namedArguments.get("cause");
            } else if (namedArguments.containsKey("throwable")) {
                causeValue = namedArguments.get("throwable");
            } else if (namedArguments.containsKey("rootCause")) {
                causeValue = namedArguments.get("rootCause");
            }

            if (causeValue != null) {
                namedArguments.put("t", causeValue);
                LOG.log(Level.FINEST, "Added alias t={0}", causeValue);
            }
        }

        // Handle boolean parameter aliases
        if (namedArguments.containsKey("suppressionEnabled") && !namedArguments.containsKey("enableSuppression")) {
            namedArguments.put("enableSuppression", namedArguments.get("suppressionEnabled"));
            LOG.log(Level.FINEST, "Added alias enableSuppression from suppressionEnabled");
        }

        if (namedArguments.containsKey("stackTraceWritable") && !namedArguments.containsKey("writableStackTrace")) {
            namedArguments.put("writableStackTrace", namedArguments.get("stackTraceWritable"));
            LOG.log(Level.FINEST, "Added alias writableStackTrace from stackTraceWritable");
        }
    }
}
