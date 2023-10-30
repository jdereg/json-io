package com.cedarsoftware.util.io.models;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
public class OuterObject {

    private int x;
    private int y;

    private MessageHolder message1Holder;

    private MessageHolder message2Holder;


    public static OuterObject of(int x, int y, String message1, String message2) {
        var object = new OuterObject();
        object.x = x;
        object.y = y;
        object.message1Holder = new MessageOneHolder(message1);
        object.message2Holder = new MessageTwoHolder(message2);
        return object;
    }

    @AllArgsConstructor
    @Getter
    private static class MessageOneHolder implements MessageHolder {
        public String message;
    }
}
