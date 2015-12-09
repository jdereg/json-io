/**
 * Replace @ref fields and array elements with referenced object.  Recursively
 * traverse JSON object tree in two passes, once to record @id's of objects, and
 * a second pass to replace @ref fields with objects identified by @id fields.
 *
 * Copyright 2010-2015 John DeRegnaucourt
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

/**
 * Use resolveRefs to process the JSON return from a server that uses json-io.
 * json-io maintains the proper shape of a graph of objects by ensuring that
 * if an object is referenced (pointed to) from more than one place, that the
 * pointed-to object is created once and all apporpriate places 'point-to' it.
 *
 * json-io does this by marking the first occurrence of the object with a
 * JSON key of {"@id": n} where n is a number.  Any other places in the object
 * graph point-to this location with an {"@ref":n}.
 *
 * Calling resolveRefs(obj) will update all the {"@ref":n} nodes in the graph
 * to be replaced with the reference to the actual object.  This is similar to
 * how ID/IDREF is used in XML.
 *
 * @param jObj the returned object after being received in Javascript from a
 * a json-io sender.  This parameter is fixed, in-place.
 *
 * Example usage:
 *
 * var result = call('someController.method', [args])
 * if (result.status === true)
 * {
 *    resolveRef(result.data);
 *    // after the above line, any @ref tags will replaced with the reference
 *    // to the object identified by the {"@ref":n} number.
 * }
 */
var resolveRefs = function(jObj)
{
    if (!jObj)
        return;

    var idsToObjs = [];

    // First pass, store all objects that have an @id field, mapped to their instance (self)
    walk(jObj, idsToObjs);

    // Replace all @ref objects with the object from the association above.
    substitute(null, null, jObj, idsToObjs);
    idsToObjs = null;
};

function walk(jObj, idsToObjs)
{
    if (!jObj)
        return;

    var keys = Object.keys(jObj); // will return an array of own properties

    for (var i = 0, len = keys.length; i < len; i++)
    {
        var field = keys[i];
        var value = jObj[field];

        if (!value)
            continue;

        if (field === "@id")
        {
            idsToObjs[value] = jObj;
        }
        else if (typeof(value) === "object")
        {
            walk(value, idsToObjs);
        }
    }
}

function substitute(parent, fieldName, jObj, idsToObjs)
{
    if (!jObj)
        return;

    var keys = Object.keys(jObj); // will return an array of own properties

    for (var i = 0, len = keys.length; i < len; i++)
    {
        var field = keys[i];
        var value = jObj[field];

        if (!value)
            continue;

        if (field === "@ref")
        {
            if (parent && fieldName)
            {
                parent[fieldName] = idsToObjs[jObj["@ref"]];
            }
        }
        else if (typeof(value) === "object")
        {
            substitute(jObj, field, value, idsToObjs);
        }
    }
}

function assert(truth)
{
    if (!truth)
    {
        throw 'assertion failed';
    }
}
