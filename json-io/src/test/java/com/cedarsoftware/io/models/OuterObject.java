package com.cedarsoftware.io.models;

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
public class OuterObject {
    private int x;
    private int y;
    private MessageHolder message1Holder;
    private MessageHolder message2Holder;

    public static OuterObject of(int x, int y, String message1, String message2) {
        OuterObject object = new OuterObject();
        object.x = x;
        object.y = y;
        object.message1Holder = new MessageOneHolder(message1);
        object.message2Holder = new MessageTwoHolder(message2);
        return object;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public MessageHolder getMessage1Holder() {
        return this.message1Holder;
    }

    public MessageHolder getMessage2Holder() {
        return this.message2Holder;
    }

    private static class MessageOneHolder implements MessageHolder {
        public String message;

        public MessageOneHolder(String message) {
            this.message = message;
        }

        public String getMessage() {
            return this.message;
        }
    }
}
