package com.cedarsoftware.io;

class WithoutCustomReaderClass
{
    private String test;
    private CustomDataClass customReaderInner;

    void setTest(String test)
    {
        this.test = test;
    }

    String getTest()
    {
        return test;
    }

    void setCustomReaderInner(CustomDataClass customReaderInner)
    {
        this.customReaderInner = customReaderInner;
    }

    CustomDataClass getCustomReaderInner()
    {
        return customReaderInner;
    }
}
