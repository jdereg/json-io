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
 *      <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
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

        if ("@id" === field || '$id' === field)
        {
            idsToObjs[value] = jObj;
        }
        else if ("object" === typeof(value))
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

        if ("@ref" === field)
        {
            if (parent && fieldName)
            {
                parent[fieldName] = idsToObjs[jObj["@ref"]];
            }
        }
        else if ("$ref" === field)
        {
            if (parent && fieldName)
            {
                parent[fieldName] = idsToObjs[jObj["$ref"]];
            }
        }
        else if ("object" === typeof(value))
        {
            substitute(jObj, field, value, idsToObjs);
        }
    }
}

/**
 * Get an HTTP GET command URL for use when the Ajax (JSON) command
 * to be sent to the command servlet has a streaming return type.
 * @param target String in the form of 'controller.method'
 * @param args Array of arguments to be passed to the method.
 */
function stream(target, args)
{
    return buildJsonCmdUrl(target) + '?json=' + buildJsonArgs(args);
}

function buildJsonCmdUrl(target)
{
    var pieces = target.split('.');
    if (pieces == null || pieces.length != 2)
    {
        throw "Error: Use 'Controller.method'";
    }
    var controller = pieces[0];
    var method = pieces[1];

    var regexp = /\/([^\/]+)\//g;
    var match = regexp.exec(location.pathname);
    if (match == null || match.length != 2)
    {
        return location.protocol + '//' + location.hostname + ":" + location.port + "/cmd/" + controller + "/" + method;
    }
    var ctx = match[1];
    return location.protocol + '//' + location.hostname + ":" + location.port + "/" + ctx + "/cmd/" + controller + "/" + method;
}

function buildJsonArgs(args)
{
    if (!args)
    {
        args = [];  // empty args
    }

    return encodeURI(JSON.stringify(args));
}

function assert(truth)
{
    if (!truth)
    {
        throw 'assertion failed';
    }
}

// functionality to replace reused objects with @ref, assuming this object has @ids or $ids
var reinsertRefs = function(jObj) {
    if (!jObj) return;
    var objsToIds = new WeakMap();
    
    desubstitute(null, null, jObj, objsToIds);
    objsToIds = null;
};

function desubstitute(parent, fieldName, jObj, objsToIds) {
    if (!jObj) return;

    var keys = Object.keys(jObj);
    for (var i = 0, len = keys.length; i < len; i++) {
        var field = keys[i];
        var value = jObj[field];

        if (!value) continue;

        if (("@id" === field || '$id' === field) && !objsToIds.has(jObj)) { objsToIds.set(jObj, jObj[field]); }
        else if (("@id" === field || '$id' === field) && parent && fieldName) { parent[fieldName] = { "@ref": objsToIds.get(jObj) }; break; }
        else if ("object" === typeof(value)) { desubstitute(jObj, field, value, objsToIds); }
    }
}
