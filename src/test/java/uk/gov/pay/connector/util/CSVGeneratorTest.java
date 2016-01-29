package uk.gov.pay.connector.util;

import com.google.common.collect.ImmutableMap;
import junit.framework.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CSVGeneratorTest {

    @Test
    public void testGenerate() throws Exception {
        ArrayList<Map<String, Object>> objectMapList = new ArrayList<>();
        objectMapList.add(ImmutableMap.of("key-1","value-1a","key-2","value-2a","key-3","value-3a"));
        objectMapList.add(ImmutableMap.of("key-1", "value-1b", "key-2", "value-2b", "key-3", "value-3b"));

        String generate = CSVGenerator.generate(objectMapList);
        String expectedOutput = "key-1,key-2,key-3\n" +
                "value-1a,value-2a,value-3a\n" +
                "value-1b,value-2b,value-3b\n";
        
        assertEquals(expectedOutput, generate);
    }
}