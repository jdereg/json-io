package com.cedarsoftware.io;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Created by jderegnaucourt on 2015/02/21.
 */
public class MapsToClassesTest
{
    @Test
    public void testMapsToClasses()
    {
        String json = "{\n" + "  \"@type\":\"com.cedarsoftware.io.MapsToClassesTest$Person\",\n" + "  \"fname\":\"Clark\",\n" + "  \"lname\":\"Kent\",\n" + "  \"age\":40,\n" + "  \"address\":{\n" + "    \"street\":\"1000 Superhighway\",\n" + "    \"city\":\"Metropolis\",\n" + "    \"state\":\"NY\",\n" + "    \"zip\":\"10001\"\n" + "  },\n" + "  \"pets\":[\n" + "    {\"name\":\"eddie\",\"type\":\"dog\"},\n" + "    {\"name\":\"bella\",\"type\":\"chi hua hua\"}\n" + "  ],\n" + "  \"pets2\":[{\"name\":\"eddie\",\"type\":\"dog\"},{\"name\":\"bella\",\"type\":\"chi hua hua\"}]\n" + "}";

        Person superMan = TestUtil.toObjects(json, null);
        assert superMan.getFname().equals("Clark");
        assert superMan.getLname().equals("Kent");
        assert superMan.getAge().equals("40");// int was converted to String

        assert superMan.getAddress() != null;
        assert superMan.getAddress().getStreet().equals("1000 Superhighway");
        assert superMan.getAddress().getCity().equals("Metropolis");
        assert superMan.getAddress().getState().equals("NY");
        assert superMan.getAddress().getZip().equals("10001");

        assert superMan.getPets() != null;
        assert superMan.getPets().length == 2;
        Pet pet1 = superMan.getPets()[0];
        assert pet1.getName().equals("eddie");
        assert pet1.getType().equals("dog");
        Pet pet2 = superMan.getPets()[1];
        assert pet2.getName().equals("bella");
        assert pet2.getType().equals("chi hua hua");

        assert superMan.getPets2() != null;
        assert superMan.getPets2().length == 2;
        Map petB1 = (Map) superMan.getPets2()[0];
        assert petB1.get("name").equals("eddie");
        assert petB1.get("type").equals("dog");
        Map petB2 = (Map) superMan.getPets2()[1];
        assert petB2.get("name").equals("bella");
        assert petB2.get("type").equals("chi hua hua");
    }

    @Test
    public void testAllTypesEmptyString()
    {
        String json = "{\n" + "    \"@type\":\"com.cedarsoftware.io.MapsToClassesTest$AllTypes\",\n" + "    \"aBoolean\":\"\",\n" + "    \"aByte\":\"\",\n" + "    \"aShort\":\"\",\n" + "    \"anInt\":\"\",\n" + "    \"aLong\":\"\",\n" + "    \"aFloat\":\"\",\n" + "    \"aDouble\":\"\",\n" + "    \"aString\":\"\",\n" + "    \"aDate\":\"\",\n" + "    \"aByteWrap\":\"\",\n" + "    \"aShortWrap\":\"\",\n" + "    \"anIntWrap\":\"\",\n" + "    \"aLongWrap\":\"\",\n" + "    \"aFloatWrap\":\"\",\n" + "    \"aDoubleWrap\":\"\",\n" + "    \"atomicBoolean\":\"\",\n" + "    \"atomicInteger\":\"\",\n" + "    \"atomicLong\":\"\"\n" + "}";

        AllTypes types = TestUtil.toObjects(json, null);
        assert !types.getaBoolean();
        assert types.getaByte() == (byte) 0;
        assert types.getaShort() == (short) 0;
        assert types.getAnInt() == 0;
        assert types.getaLong() == (long) 0;
        assert types.getaFloat() == 0.0f;
        assert types.getaDouble() == 0.0d;
        assert types.getaString().equals("");
        assert types.getaDate() == null;
        assert types.getaByteWrap() == (byte) 0;
        assert types.getaShortWrap() == (short) 0;
        assert types.getAnIntWrap() == 0;
        assert types.getaLongWrap() == (long) 0;
        assert types.getaFloatWrap() == 0.0f;
        assert types.getaDoubleWrap() == 0.0d;
        assert types.atomicBoolean.get() == false;
        assert types.atomicInteger.get() == 0;
        assert types.atomicLong.get() == 0;
    }

    @Test
    public void testAllTypesNull()
    {
        String json = "{\n" + "    \"@type\":\"com.cedarsoftware.io.MapsToClassesTest$AllTypes\",\n" + "    \"aBoolean\":null,\n" + "    \"aByte\":null,\n" + "    \"aShort\":null,\n" + "    \"anInt\":null,\n" + "    \"aLong\":null,\n" + "    \"aFloat\":null,\n" + "    \"aDouble\":null,\n" + "    \"aString\":null,\n" + "    \"aDate\":null,\n" + "    \"aByteWrap\":null,\n" + "    \"aShortWrap\":null,\n" + "    \"anIntWrap\":null,\n" + "    \"aLongWrap\":null,\n" + "    \"aFloatWrap\":null,\n" + "    \"aDoubleWrap\":null,\n" + "    \"atomicBoolean\":null,\n" + "    \"atomicInteger\":null,\n" + "    \"atomicLong\":null\n" + "}";
        AllTypes types = TestUtil.toObjects(json, null);
        assert !types.getaBoolean();
        assert types.getaByte() == (byte) 0;
        assert types.getaShort() == (short) 0;
        assert types.getAnInt() == 0;
        assert types.getaLong() == (long) 0;
        assert types.getaFloat() == 0.0f;
        assert types.getaDouble() == 0.0d;
        assert types.getaString() == null;
        assert types.getaDate() == null;
        assert types.getaByteWrap() == null;
        assert types.getaShortWrap() == null;
        assert types.getAnIntWrap() == null;
        assert types.getaLongWrap() == null;
        assert types.getaFloatWrap() == null;
        assert types.getaDoubleWrap() == null;
        assert types.getAtomicBoolean() == null;
        assert types.getAtomicInteger() == null;
        assert types.getAtomicLong() == null;
    }

    public static class Person
    {
        public String getFname()
        {
            return fname;
        }

        public void setFname(String fname)
        {
            this.fname = fname;
        }

        public String getLname()
        {
            return lname;
        }

        public void setLname(String lname)
        {
            this.lname = lname;
        }

        public String getAge()
        {
            return age;
        }

        public void setAge(String age)
        {
            this.age = age;
        }

        public Address getAddress()
        {
            return address;
        }

        public void setAddress(Address address)
        {
            this.address = address;
        }

        public Pet[] getPets()
        {
            return pets;
        }

        public void setPets(Pet[] pets)
        {
            this.pets = pets;
        }

        public Object[] getPets2()
        {
            return pets2;
        }

        public void setPets2(Object[] pets2)
        {
            this.pets2 = pets2;
        }

        private String fname;
        private String lname;
        private String age;
        private Address address;
        private Pet[] pets;
        private Object[] pets2;
    }

    public static class Address
    {
        public String getStreet()
        {
            return street;
        }

        public void setStreet(String street)
        {
            this.street = street;
        }

        public String getCity()
        {
            return city;
        }

        public void setCity(String city)
        {
            this.city = city;
        }

        public String getState()
        {
            return state;
        }

        public void setState(String state)
        {
            this.state = state;
        }

        public String getZip()
        {
            return zip;
        }

        public void setZip(String zip)
        {
            this.zip = zip;
        }

        private String street;
        private String city;
        private String state;
        private String zip;
    }

    public static class Pet
    {
        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        private String name;
        private String type;
    }

    public static class AllTypes
    {
        public boolean getaBoolean()
        {
            return aBoolean;
        }

        public boolean isaBoolean()
        {
            return aBoolean;
        }

        public void setaBoolean(boolean aBoolean)
        {
            this.aBoolean = aBoolean;
        }

        public byte getaByte()
        {
            return aByte;
        }

        public void setaByte(byte aByte)
        {
            this.aByte = aByte;
        }

        public short getaShort()
        {
            return aShort;
        }

        public void setaShort(short aShort)
        {
            this.aShort = aShort;
        }

        public int getAnInt()
        {
            return anInt;
        }

        public void setAnInt(int anInt)
        {
            this.anInt = anInt;
        }

        public long getaLong()
        {
            return aLong;
        }

        public void setaLong(long aLong)
        {
            this.aLong = aLong;
        }

        public float getaFloat()
        {
            return aFloat;
        }

        public void setaFloat(float aFloat)
        {
            this.aFloat = aFloat;
        }

        public double getaDouble()
        {
            return aDouble;
        }

        public void setaDouble(double aDouble)
        {
            this.aDouble = aDouble;
        }

        public String getaString()
        {
            return aString;
        }

        public void setaString(String aString)
        {
            this.aString = aString;
        }

        public Date getaDate()
        {
            return aDate;
        }

        public void setaDate(Date aDate)
        {
            this.aDate = aDate;
        }

        public Byte getaByteWrap()
        {
            return aByteWrap;
        }

        public void setaByteWrap(Byte aByteWrap)
        {
            this.aByteWrap = aByteWrap;
        }

        public Short getaShortWrap()
        {
            return aShortWrap;
        }

        public void setaShortWrap(Short aShortWrap)
        {
            this.aShortWrap = aShortWrap;
        }

        public Integer getAnIntWrap()
        {
            return anIntWrap;
        }

        public void setAnIntWrap(Integer anIntWrap)
        {
            this.anIntWrap = anIntWrap;
        }

        public Long getaLongWrap()
        {
            return aLongWrap;
        }

        public void setaLongWrap(Long aLongWrap)
        {
            this.aLongWrap = aLongWrap;
        }

        public Float getaFloatWrap()
        {
            return aFloatWrap;
        }

        public void setaFloatWrap(Float aFloatWrap)
        {
            this.aFloatWrap = aFloatWrap;
        }

        public Double getaDoubleWrap()
        {
            return aDoubleWrap;
        }

        public void setaDoubleWrap(Double aDoubleWrap)
        {
            this.aDoubleWrap = aDoubleWrap;
        }

        public AtomicBoolean getAtomicBoolean()
        {
            return atomicBoolean;
        }

        public void setAtomicBoolean(AtomicBoolean atomicBoolean)
        {
            this.atomicBoolean = atomicBoolean;
        }

        public AtomicInteger getAtomicInteger()
        {
            return atomicInteger;
        }

        public void setAtomicInteger(AtomicInteger atomicInteger)
        {
            this.atomicInteger = atomicInteger;
        }

        public AtomicLong getAtomicLong()
        {
            return atomicLong;
        }

        public void setAtomicLong(AtomicLong atomicLong)
        {
            this.atomicLong = atomicLong;
        }

        private boolean aBoolean;
        private byte aByte;
        private short aShort;
        private int anInt;
        private long aLong;
        private float aFloat;
        private double aDouble;
        private String aString;
        private Date aDate;
        private Byte aByteWrap;
        private Short aShortWrap;
        private Integer anIntWrap;
        private Long aLongWrap;
        private Float aFloatWrap;
        private Double aDoubleWrap;
        private AtomicBoolean atomicBoolean;
        private AtomicInteger atomicInteger;
        private AtomicLong atomicLong;
    }
}
