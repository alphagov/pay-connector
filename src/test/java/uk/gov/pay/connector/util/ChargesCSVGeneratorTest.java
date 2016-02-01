package uk.gov.pay.connector.util;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ChargesCSVGeneratorTest {

    @Test
    public void testGenerateWithMappedHeaders() throws Exception {
        ArrayList<Map<String, Object>> objectMapList = new ArrayList<>();
        objectMapList.add(ImmutableMap.of("reference","value-1a","charge_id","value-2a","gateway_transaction_id","value-3a"));
        objectMapList.add(ImmutableMap.of("reference", "value-1b", "charge_id", "value-2b", "gateway_transaction_id", "value-3b"));

        String generate = ChargesCSVGenerator.generate(objectMapList);
        String expectedOutput = "Service Payment Reference,Charge ID,Gateway Transaction ID\n" +
                "value-1a,value-2a,value-3a\n" +
                "value-1b,value-2b,value-3b\n";
        
        assertEquals(expectedOutput, generate);
    }

    @Test
    public void testGenerateWithUnmappedHeaders() throws Exception {
        ArrayList<Map<String, Object>> objectMapList = new ArrayList<>();
        objectMapList.add(ImmutableMap.of("something","value-1a","charge_id","value-2a","gateway_transaction_id","value-3a"));
        objectMapList.add(ImmutableMap.of("something", "value-1b", "charge_id", "value-2b", "gateway_transaction_id", "value-3b"));

        String generate = ChargesCSVGenerator.generate(objectMapList);
        String expectedOutput = "something,Charge ID,Gateway Transaction ID\n" +
                "value-1a,value-2a,value-3a\n" +
                "value-1b,value-2b,value-3b\n";

        assertEquals(expectedOutput, generate);
    }
}