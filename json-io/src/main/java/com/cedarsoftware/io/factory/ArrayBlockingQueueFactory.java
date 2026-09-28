package com.cedarsoftware.io.factory;

import java.util.concurrent.ArrayBlockingQueue;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;

/**
 * Factory class to create an empty {@link ArrayBlockingQueue}, which the resolver then fills from {@code @items}.
 * <p>
 * An ArrayBlockingQueue has no no-argument constructor -- it needs its capacity -- so reading one failed with
 * "Unable to instantiate". Its capacity is not written, so the queue is made as large as its items (at least 1), as
 * Jackson does.
 *
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
public class ArrayBlockingQueueFactory implements ClassFactory {

    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object[] items = jObj.getItems();
        ArrayBlockingQueue<Object> queue = new ArrayBlockingQueue<>(Math.max(items == null ? 0 : items.length, 1));
        jObj.setTarget(queue);
        return queue;
    }
}
