package com.cedarsoftware.util.io;




import org.junit.Test;



public class TestRecords
{

    public record TestRecordMember(String str, Boolean bool)
    {

    }



    @Test
    public void testMemberRecord()
    {
        TestRecordMember rec = new TestRecordMember("check", false);

        String strJson = JsonWriter.objectToJson(rec);

        TestRecordMember clone = (TestRecordMember) JsonReader.jsonToJava(strJson);


        assert clone.bool == rec.bool;
        assert clone.str.equals(rec.str);
    }

    @Test
    public void testInnerRecord()
    {

        record TestRecordLocal(String str, boolean bool)
        {

        }
        TestRecordLocal rec = new TestRecordLocal("check", false);

        String strJson = JsonWriter.objectToJson(rec);

        TestRecordLocal clone = (TestRecordLocal) JsonReader.jsonToJava(strJson);


        assert clone.bool == rec.bool;
        assert clone.str.equals(rec.str);
    }


}
