package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import static org.junit.Assert.*;

public class HalResourceBuilderTest {

    @Test
    public void shouldGetChargeTransactionHal() throws Exception {
        String result = new HalResourceBuilder()
                .withProperty("count", 100)
                .withProperty("total", 300)
                .withSelfLink("/self")
                .withLink("first_page", "?page=1")
                .withLink("next_page", "?page=3")
                .withLink("last_page", "?page=5")
                .withLink("previous_page", "?page=2")
                .withProperty("results", ImmutableList.of("one", "two"))
                .build();

        assertEquals("hal response mismatch",
                "{" +
                        "\"total\":300," +
                        "\"count\":100," +
                        "\"results\":[\"one\",\"two\"]," +
                        "\"_links\":" +
                            "{\"next_page\":{\"href\":\"?page=3\"}," +
                            "\"self\":{\"href\":\"/self\"}," +
                            "\"previous_page\":{\"href\":\"?page=2\"}," +
                            "\"last_page\":{\"href\":\"?page=5\"}," +
                            "\"first_page\":{\"href\":\"?page=1\"}" +
                        "}" +
                "}",
                result);
    }
}