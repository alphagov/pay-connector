package uk.gov.pay.connector.util;

import com.google.common.io.ByteStreams;
import org.testcontainers.shaded.com.google.common.io.Resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TestResourceLoader {
    public static String loadResource(String filename) {
        return load(filename);
    }

    private static String load(String filename) {
        try (InputStream inputStream = Resources.getResource(filename).openStream()){
            String fileContent = (new String(ByteStreams.toByteArray(inputStream), StandardCharsets.UTF_8)).trim();
            return fileContent;
        } catch (IOException ioException) {
            throw new IllegalArgumentException(ioException);
        }
    }
}
