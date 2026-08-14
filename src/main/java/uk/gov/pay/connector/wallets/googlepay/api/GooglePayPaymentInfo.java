package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.gateway.model.BrowserDataFor3ds;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.util.Optional;

public class GooglePayPaymentInfo extends WalletPaymentInfo implements BrowserDataFor3ds {
    
    @Schema(example = "text/html;q=1.0, */*;q=0.9")
    @JsonProperty("accept_header")
    private String acceptHeader;
    
    @Schema(example = "Mozilla/5.0")
    @JsonProperty("user_agent_header")
    private String userAgentHeader;
    
    @Schema(example = "203.0.113.1")
    @JsonProperty("ip_address")
    private String ipAddress;
    
    @Schema(example = "true")
    @JsonProperty("js_enabled")
    private Boolean jsEnabled;

    @Schema(example = "en-GB")
    @JsonProperty("js_navigator_language")
    private String jsNavigatorLanguage;

    @Schema(example = "32")
    @JsonProperty("js_screen_color_depth")
    private String jsScreenColorDepth;

    @Schema(example = "982")
    @JsonProperty("js_screen_height")
    private String jsScreenHeight;

    @Schema(example = "1512")
    @JsonProperty("js_screen_width")
    private String jsScreenWidth;

    @Schema(example = "-60")
    @JsonProperty("js_timezone_offset_mins")
    private String jsTimezoneOffsetMins;

    @Schema(example = "1f1154b7-620d-4654-801b-893b5bb22db1", description = "SessionId returned by Worldpay/CardinalCommerce as part of device data collection. Applicable for Google Pay payments only")
    @JsonProperty("worldpay_3ds_flex_ddc_result")
    private String worldpay3dsFlexDdcResult;

    public GooglePayPaymentInfo() {
        // Needed for Jackson
    }
    
    public GooglePayPaymentInfo(String lastDigitsCardNumber,
                                String brand,
                                PayersCardType cardType,
                                String cardholderName,
                                String email,
                                String acceptHeader,
                                String userAgentHeader,
                                String ipAddress,
                                String worldpay3dsFlexDdcResult) {
        super(lastDigitsCardNumber, brand, cardType, cardholderName, email);
        this.acceptHeader = acceptHeader;
        this.userAgentHeader = userAgentHeader;
        this.ipAddress = ipAddress;
        this.worldpay3dsFlexDdcResult = worldpay3dsFlexDdcResult;
    }

    @Override
    public Optional<String> getBrowserAcceptHeader() {
        return Optional.ofNullable(acceptHeader);
    }

    @Override
    public Optional<String> getBrowserUserAgent() {
        return Optional.ofNullable(userAgentHeader);
    }

    @Override
    public Optional<String> getBrowserIpAddress() {
        return Optional.ofNullable(ipAddress);
    }

    @Override
    public Optional<Boolean> getBrowserJavaScriptEnabled() {
        return Optional.ofNullable(jsEnabled);
    }

    @Override
    public Optional<String> getBrowserLanguage() {
        return Optional.ofNullable(jsNavigatorLanguage);
    }

    @Override
    public Optional<String> getBrowserColorDepth() {
        return Optional.ofNullable(jsScreenColorDepth);
    }

    @Override
    public Optional<String> getBrowserScreenHeight() {
        return Optional.ofNullable(jsScreenHeight);
    }

    @Override
    public Optional<String> getBrowserScreenWidth() {
        return Optional.ofNullable(jsScreenWidth);
    }

    @Override
    public Optional<String> getBrowserTZ() {
        return Optional.ofNullable(jsTimezoneOffsetMins);
    }

    public Optional<String> getWorldpay3dsFlexDdcResult() {
        return Optional.ofNullable(worldpay3dsFlexDdcResult);
    }

    @Override
    public String toString() {
        return "GooglePayPaymentInfo{" +
                "lastDigitsCardNumber='" + lastDigitsCardNumber + '\'' +
                ", brand='" + brand + '\'' +
                ", cardType=" + cardType +
                ", acceptHeader=" + acceptHeader +
                ", userAgentHeader=" + userAgentHeader +
                ", ipAddress=" + Optional.ofNullable(ipAddress).map(x -> "ipAddress is present").orElse("ipAddress is not present") +
                ". jsEnabled=" + jsEnabled +
                ", jsNavigatorLanguage='" + jsNavigatorLanguage + '\'' +
                ", jsScreenColorDepth='" + jsScreenColorDepth + '\'' +
                ", jsScreenHeight='" + jsScreenHeight + '\'' +
                ", jsScreenWidth='" + jsScreenWidth + '\'' +
                ", jsTimezoneOffsetMins='" + jsTimezoneOffsetMins + '\'' +
                ", worldpay3dsFlexDdcResult=" + getWorldpay3dsFlexDdcResult().map(x -> "present").orElse("not present") +
                '}';
    }
}
