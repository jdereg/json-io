package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by jderegnaucourt on 2015/02/21.
 */
class TestMapsToClasses
{
    static class Person
    {
        String fname
        String lname
        String age
        Address address
        Pet[] pets
        Object[] pets2
    }

    static class Address
    {
        String street
        String city
        String state
        String zip
    }

    static class Pet
    {
        String name
        String type
    }

    static class AllTypes
    {
        boolean aBoolean
        byte aByte
        short aShort
        int anInt
        long aLong
        float aFloat
        double aDouble
        String aString
        Date aDate
        Byte aByteWrap
        Short aShortWrap
        Integer anIntWrap
        Long aLongWrap
        Float aFloatWrap
        Double aDoubleWrap
        AtomicBoolean atomicBoolean
        AtomicInteger atomicInteger
        AtomicLong atomicLong
    }

    @Test
    void testMapsToClasses()
    {
        String json =
                """{
  "@type":"com.cedarsoftware.util.io.TestMapsToClasses\$Person",
  "fname":"Clark",
  "lname":"Kent",
  "age":40,
  "address":{
    "street":"1000 Superhighway",
    "city":"Metropolis",
    "state":"NY",
    "zip":"10001"
  },
  "pets":[
    {"name":"eddie","type":"dog"},
    {"name":"bella","type":"chi hua hua"}
  ],
  "pets2":[{"name":"eddie","type":"dog"},{"name":"bella","type":"chi hua hua"}]
}"""

        Person superMan = JsonReader.jsonToJava(json)
        assert superMan.fname == 'Clark'
        assert superMan.lname == 'Kent'
        assert superMan.age == '40'       // int was converted to String

        assert superMan.address instanceof Address
        assert superMan.address.street == "1000 Superhighway"
        assert superMan.address.city == "Metropolis"
        assert superMan.address.state == "NY"
        assert superMan.address.zip == "10001"

        assert superMan.pets != null
        assert superMan.pets.length == 2
        Pet pet1 = superMan.pets[0]
        assert pet1.name == "eddie"
        assert pet1.type == "dog"
        Pet pet2 = superMan.pets[1]
        assert pet2.name == "bella"
        assert pet2.type == "chi hua hua"

        assert superMan.pets2 != null
        assert superMan.pets2.length == 2
        Object petB1 = superMan.pets2[0]
        assert petB1 instanceof Map
        petB1.name == "eddie"
        petB1.type == "dog"
        Object petB2 = superMan.pets2[0]
        assert petB2 instanceof Map
        petB2.name == "bella"
        petB2.type == "chi hua hua"
    }

    @Test
    void testAllTypesEmptyString()
    {
        String json =
                """{
    "@type":"com.cedarsoftware.util.io.TestMapsToClasses\$AllTypes",
    "aBoolean":"",
    "aByte":"",
    "aShort":"",
    "anInt":"",
    "aLong":"",
    "aFloat":"",
    "aDouble":"",
    "aString":"",
    "aDate":"",
    "aByteWrap":"",
    "aShortWrap":"",
    "anIntWrap":"",
    "aLongWrap":"",
    "aFloatWrap":"",
    "aDoubleWrap":"",
    "atomicBoolean":"",
    "atomicInteger":"",
    "atomicLong":""
}
"""
        AllTypes types = JsonReader.jsonToJava(json)
        assert types.aBoolean == false
        assert types.aByte == (byte)0
        assert types.aShort == (short)0
        assert types.anInt == (int)0
        assert types.aLong == (long)0
        assert types.aFloat == 0.0f
        assert types.aDouble == 0.0d
        assert types.aString == ""
        assert types.aDate == null
        assert types.aByteWrap == (byte)0
        assert types.aShortWrap == (short)0
        assert types.anIntWrap == (int)0
        assert types.aLongWrap == (long)0
        assert types.aFloatWrap == 0.0f
        assert types.aDoubleWrap == 0.0d
//        assert types.atomicBoolean.get() == false
//        assert types.atomicInteger.get() == 0i
//        assert types.atomicLong.get() == 0L
    }

    @Test
    void testAllTypesNull()
    {
        String json =
                """{
    "@type":"com.cedarsoftware.util.io.TestMapsToClasses\$AllTypes",
    "aBoolean":null,
    "aByte":null,
    "aShort":null,
    "anInt":null,
    "aLong":null,
    "aFloat":null,
    "aDouble":null,
    "aString":null,
    "aDate":null,
    "aByteWrap":null,
    "aShortWrap":null,
    "anIntWrap":null,
    "aLongWrap":null,
    "aFloatWrap":null,
    "aDoubleWrap":null,
    "atomicBoolean":null,
    "atomicInteger":null,
    "atomicLong":null
}
"""
        AllTypes types = JsonReader.jsonToJava(json)
        assert types.aBoolean == false
        assert types.aByte == (byte)0
        assert types.aShort == (short)0
        assert types.anInt == (int)0
        assert types.aLong == (long)0
        assert types.aFloat == 0.0f
        assert types.aDouble == 0.0d
        assert types.aString == null
        assert types.aDate == null
        assert types.aByteWrap == null
        assert types.aShortWrap == null
        assert types.anIntWrap == null
        assert types.aLongWrap == null
        assert types.aFloatWrap == null
        assert types.aDoubleWrap == null
        assert types.atomicBoolean == null
        assert types.atomicInteger == null
        assert types.atomicLong == null
    }
}
