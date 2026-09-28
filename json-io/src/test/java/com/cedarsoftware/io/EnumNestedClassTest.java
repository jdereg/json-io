package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An object of a class declared inside an enum is written and read as the object it is. java-util's
 * getClassIfEnum() reported such a class as its enclosing enum, so json-io chose the enum writer for it and failed
 * ("Status$Detail cannot be cast to java.lang.Enum"), on every JDK. Enum constants with bodies and enums nested in
 * enums still read and write as enums.
 */
class EnumNestedClassTest {

    public enum Status {
        ACTIVE,
        CLOSED {
            @Override
            public boolean isFinal() {
                return true;
            }
        };

        public boolean isFinal() {
            return false;
        }

        public static class Detail {
            public String note;
            public int code;
        }

        public enum Reason { TIMEOUT, USER }
    }

    public static class Holder {
        public Status.Detail detail;
        public Object any;
        public Status status;
        public Status.Reason reason;
    }

    @Test
    void aClassDeclaredInsideAnEnumRoundTrips() {
        Status.Detail detail = new Status.Detail();
        detail.note = "n";
        detail.code = 7;
        Status.Detail read = JsonIo.toJava(JsonIo.toJson(detail, null), null).asClass(Status.Detail.class);
        assertThat(read.note).isEqualTo("n");
        assertThat(read.code).isEqualTo(7);

        Holder holder = new Holder();
        holder.detail = detail;
        holder.any = detail;
        holder.status = Status.CLOSED;
        holder.reason = Status.Reason.USER;
        Holder back = JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class);
        assertThat(back.detail.note).isEqualTo("n");
        assertThat(back.any).isInstanceOf(Status.Detail.class);
        assertThat(((Status.Detail) back.any).code).isEqualTo(7);
        assertThat(back.status).isSameAs(Status.CLOSED);
        assertThat(back.status.isFinal()).isTrue();
        assertThat(back.reason).isSameAs(Status.Reason.USER);
    }
}
