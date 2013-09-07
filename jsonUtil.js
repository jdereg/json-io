/**
 * Replace @ref fields and array elements with referenced object.  Recursively
 * traverse JSON object tree in two passes, once to record @id's of objects, and
 * a second pass to replace @ref fields with objects identified by @id fields.
 *
 * Copyright 2010 John DeRegnaucourt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

function resolveRefs(jObj)
{
    var idsToObjs = [];

    // First pass, store all objects that have an @ID field, mapped to their instance (self)
    walk(jObj, idsToObjs);

    // Replace all @ref: objects with the object from the association above.
    substitute(null, null, jObj, idsToObjs);

    idsToObjs = null;
}

function walk(jObj, idsToObjs)
{
    for (var field in jObj)
    {
        var value = jObj[field];
        if (field == "@id")
        {
            idsToObjs[value] = jObj;
        }
        else if (typeof(value) == "object")
        {
            walk(value, idsToObjs);
        }
    }
}

function substitute(parent, fieldName, jObj, idsToObjs)
{
    for (var field in jObj)
    {
        var value = jObj[field];
        if (field == "@ref")
        {
            if (parent != null && fieldName != null)
            {
                parent[fieldName] = idsToObjs[jObj["@ref"]];
            }
        }
        else if (typeof(value) == "object")
        {
            substitute(jObj, field, value, idsToObjs);
        }
    }
}
