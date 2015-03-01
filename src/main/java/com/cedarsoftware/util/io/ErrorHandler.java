package com.cedarsoftware.util.io;

import java.io.IOException;

/**
 * This interface exists to allow a 'function pointer' to be passed to the
 * functional methods in MetaUtils.  Because JsonReader maintains the line
 * and column position of where it is inside the JSON being parsed, it is
 * very useful to include that in all error messages.  This 'state' is not
 * accessible to MetaUtils, so MetaUtils calls the 'ErrorHandler' supplied
 * to it, allowing the Reader to plug in an error handler that includes the
 * position information.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.*
 */

public interface ErrorHandler
{
    Object error(String msg) throws IOException;
    Object error(String msg, Exception e) throws IOException;
}
