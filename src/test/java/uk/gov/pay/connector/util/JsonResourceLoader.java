package uk.gov.pay.connector.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class JsonResourceLoader {
    private static JsonElement loadJsonFromFile(String filePath) throws FileNotFoundException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        return JsonParser.parseReader(reader);
    }

    public static String load(String location) throws FileNotFoundException {
        String filePath = JsonResourceLoader.class.getClassLoader().getResource(location).getPath();
        return loadJsonFromFile(filePath).toString();
    }

}
