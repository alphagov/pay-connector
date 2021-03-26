package uk.gov.pay.connector.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.pay.connector.common.model.domain.Address;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.PayersCardType.CREDIT_OR_DEBIT;

@ValidAuthCardDetails
public class AuthCardDetails implements AuthorisationDetails {

    private String cardNo;
    private String cardHolder;
    private String cvc;
    private CardExpiryDate endDate;
    private Address address;
    private String cardBrand;
    private String userAgentHeader;
    private String acceptHeader;
    private PayersCardType payersCardType;
    private PayersCardPrepaidStatus payersCardPrepaidStatus;
    private Boolean corporateCard;
    private String worldpay3dsFlexDdcResult;
    private String ipAddress;
    private String jsScreenColorDepth;
    private String jsNavigatorLanguage;
    private String jsScreenHeight;
    private String jsScreenWidth;
    private String jsTimezoneOffsetMins;

    public static AuthCardDetails anAuthCardDetails() {
        return new AuthCardDetails();
    }

    public Optional<String> getJsTimezoneOffsetMins() {
        return Optional.ofNullable(jsTimezoneOffsetMins);
    }

    @JsonProperty("js_timezone_offset_min")
    public void setJsTimezoneOffsetMins(String jsTimezoneOffsetMins) {
        this.jsTimezoneOffsetMins = jsTimezoneOffsetMins;
    }

    public Optional<String> getJsScreenHeight() {
        return Optional.ofNullable(jsScreenHeight);
    }

    @JsonProperty("js_screen_height")
    public void setJsScreenHeight(String jsScreenHeight) {
        this.jsScreenHeight = jsScreenHeight;
    }

    public Optional<String> getJsScreenWidth() {
        return Optional.ofNullable(jsScreenWidth);
    }

    @JsonProperty("js_screen_width")
    public void setJsScreenWidth(String jsScreenWidth) {
        this.jsScreenWidth = jsScreenWidth;
    }

    public Optional<String> getJsScreenColorDepth() {
        return Optional.ofNullable(jsScreenColorDepth);
    }

    @JsonProperty("js_screen_color_depth")
    public void setJsScreenColorDepth(String jsScreenColorDepth) {
        this.jsScreenColorDepth = jsScreenColorDepth;
    }

    @JsonProperty("card_number")
    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    @JsonProperty("card_brand")
    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    @JsonProperty("cardholder_name")
    public void setCardHolder(String cardHolder) {
        this.cardHolder = cardHolder;
    }

    @JsonProperty("cvc")
    public void setCvc(String cvc) {
        this.cvc = cvc;
    }

    @JsonProperty("expiry_date")
    @JsonSerialize(using = ToStringSerializer.class)
    public void setEndDate(CardExpiryDate endDate) {
        this.endDate = endDate;
    }

    @JsonProperty("address")
    public void setAddress(Address address) {
        this.address = address;
    }

    @JsonProperty("user_agent_header")
    public void setUserAgentHeader(String userAgentHeader) {
        this.userAgentHeader = userAgentHeader;
    }

    @JsonProperty("accept_header")
    public void setAcceptHeader(String acceptHeader) {
        this.acceptHeader = acceptHeader;
    }

    @JsonProperty("corporate_card")
    public void setCorporateCard(Boolean corporateCard) {
        this.corporateCard = corporateCard;
    }

    @JsonProperty("card_type")
    public void setPayersCardType(PayersCardType payersCardType) {
        this.payersCardType = payersCardType;
    }

    @JsonProperty("prepaid")
    public void setPayersCardPrepaidStatus(PayersCardPrepaidStatus payersCardPrepaidStatus) {
        this.payersCardPrepaidStatus = payersCardPrepaidStatus;
    }

    @JsonProperty("worldpay_3ds_flex_ddc_result")
    public void setWorldpay3dsFlexDdcResult(String worldpay3dsFlexDdcResult) {
        this.worldpay3dsFlexDdcResult = worldpay3dsFlexDdcResult;
    }

    @JsonProperty("ip_address")
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getCardNo() {
        return cardNo;
    }

    public String getCardHolder() {
        return cardHolder;
    }

    public String getCvc() {
        return cvc;
    }

    public CardExpiryDate getEndDate() {
        return endDate;
    }

    public Optional<Address> getAddress() {
        return Optional.ofNullable(address);
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getUserAgentHeader() {
        return userAgentHeader;
    }

    public String getAcceptHeader() {
        return acceptHeader;
    }

    public boolean isCorporateCard() {
        return corporateCard == null ? false : corporateCard;
    }

    public PayersCardType getPayersCardType() {
        return payersCardType == null ? CREDIT_OR_DEBIT : payersCardType;
    }

    public PayersCardPrepaidStatus getPayersCardPrepaidStatus() {
        return payersCardPrepaidStatus == null ? PayersCardPrepaidStatus.UNKNOWN : payersCardPrepaidStatus;
    }

    public Optional<String> getWorldpay3dsFlexDdcResult() {
        return Optional.ofNullable(worldpay3dsFlexDdcResult);
    }

    public Optional<String> getIpAddress() {
        return Optional.ofNullable(ipAddress);
    }

    @JsonProperty("js_navigator_language")
    public void setJsNavigatorLanguage(String jsNavigatorLanguage) {
        this.jsNavigatorLanguage = jsNavigatorLanguage;
    }

    public Optional<String> getJsNavigatorLanguage() {
        return Optional.ofNullable(jsNavigatorLanguage);
    }
}
