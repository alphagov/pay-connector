package uk.gov.pay.connector.util;

import com.google.gson.Gson;

public class JsonEncoder {

    private static final Gson jsonEncoder = new Gson();

    public static String toJson(Object obj) {
        return jsonEncoder.toJson(obj);
    }
}
