package com.cedarsoftware.io;

import com.cedarsoftware.util.Converter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ByteArrayTests {

    private static final String KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC08S41OaEIMWePafyclmUtBvodk2GGe+RV90VpkPuFeWz0+/V1pf0OSZ2gakW2WU91lN8/Z7VpC3VINFKUonLNZ8VT4L/n74u+YrOzsrm0JXVeVI37bi3+tQCiwrUTkcmUVbFYh26u1MgDbChNukSdw32XKO247qybb4s9zWgtE9WcZI7r0v+v3wd2c/efm9gnmAe+Nzv08VFx+txOU/Fmbu21Jhezc8EqESBEr0hPajeITmdR0LDwN4lPwtOxuI84CHNVaSEor2vjh1wLxDSEkQtZWCuZbZw1az/5D4ckggEr03jX9erlZlktmZ4SWVW3tdkbCo3ZAoGgOKPXVy7T rsa-key-20240211";

    @Test
    void testByteArray() {
        byte[] bytes = Converter.convert(KEY, byte[].class);
        String back = Converter.convert(bytes, String.class);
        assertThat(back).isEqualTo(KEY);
    }

    /* For LATER TESTS
    @Test
    void testByteArray_withJson() {
        String input = "\"" + KEY + "\"";

        byte[] converted = TestUtil.toObjects(input, byte[].class);
        assertThat(converted).isEqualTo(Converter.convert(KEY, byte[].class));
    }
    */
}
