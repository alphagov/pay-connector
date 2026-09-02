package uk.gov.pay.connector.gateway.adyen.response.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record AdyenBrowserInfo(
        @JsonProperty("acceptHeader") String acceptHeader,
        @JsonProperty("colorDepth") int colorDepth,
        @JsonProperty("javaEnabled") boolean javaEnabled,
        @JsonProperty("javaScriptEnabled") boolean javaScriptEnabled,
        @JsonProperty("language") String language,
        @JsonProperty("screenHeight") int screenHeight,
        @JsonProperty("screenWidth") int screenWidth,
        @JsonProperty("timeZoneOffset") int timeZoneOffset,
        @JsonProperty("userAgent") String userAgent
) {}
