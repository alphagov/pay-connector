package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.util.Optional;

public class GooglePayPaymentInfo extends WalletPaymentInfo {
    
    @Schema(example = "text/html;q=1.0, */*;q=0.9")
    private String acceptHeader;
    
    @Schema(example = "Mozilla/5.0")
    private String userAgentHeader;
    
    @Schema(example = "203.0.113.1")
    private String ipAddress;
    
    @Schema(example = "1f1154b7-620d-4654-801b-893b5bb22db1", description = "SessionId returned by Worldpay/CardinalCommerce as part of device data collection. Applicable for Google Pay payments only")
    @JsonProperty("worldpay_3ds_flex_ddc_result")
    private String worldpay3dsFlexDdcResult;

    public GooglePayPaymentInfo() {
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

    public String getAcceptHeader() {
        return acceptHeader;
    }

    public String getUserAgentHeader() {
        return userAgentHeader;
    }

    public String getIpAddress() {
        return ipAddress;
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
                ", worldpay3dsFlexDdcResult=" + getWorldpay3dsFlexDdcResult().map(x -> "present").orElse("not present") +
                '}';
    }
}
