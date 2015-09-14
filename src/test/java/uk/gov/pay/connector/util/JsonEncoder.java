package uk.gov.pay.connector.util;

import com.google.gson.Gson;

import java.util.Map;

public class JsonEncoder {

    private static final Gson jsonEncoder = new Gson();

    public static String toJson(Map<?, ?> map) {
        return jsonEncoder.toJson(map);
    }
}
