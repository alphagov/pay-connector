package uk.gov.pay.connector.util;

import com.google.common.io.Resources;

import java.io.IOException;
import java.net.URL;

public abstract class WorldpayTestUtil {

    public static void setupSSL() throws IOException {

        URL url = Resources.getResource("cert.jks");
        String certPath = url.getFile();

        System.setProperty("javax.net.ssl.trustStore", certPath);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }

}
