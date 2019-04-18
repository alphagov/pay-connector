package uk.gov.pay.connector.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonEncoder {

    private static final Gson jsonEncoder = new Gson();

    public static String toJson(Object obj) {
        return jsonEncoder.toJson(obj);
    }

    public static String toJsonWithNulls(Object obj) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.serializeNulls();
        Gson jsonEncoderWithNulls = gsonBuilder.create();
        return jsonEncoderWithNulls.toJson(obj);
    }
}
