package uk.gov.pay.connector.gateway.adyen.response.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BrowserInfo(
        @JsonProperty("acceptHeader") String acceptHeader,
        @JsonProperty("colorDepth") Integer colorDepth,
        @JsonProperty("javaEnabled") Boolean javaEnabled,
        @JsonProperty("javaScriptEnabled") Boolean javaScriptEnabled,
        @JsonProperty("language") String language,
        @JsonProperty("screenHeight") Integer screenHeight,
        @JsonProperty("screenWidth") Integer screenWidth,
        @JsonProperty("timeZoneOffset") Integer timeZoneOffset,
        @JsonProperty("userAgent") String userAgent
) {}
