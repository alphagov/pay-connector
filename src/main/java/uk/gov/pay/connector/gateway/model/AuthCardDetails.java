package uk.gov.pay.connector.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.card.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.card.model.AuthoriseRequest;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Objects;
import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.PayersCardType.CREDIT_OR_DEBIT;

@ValidAuthCardDetails
public class AuthCardDetails {

    @Schema(example = "4242424242424242", description = "Card number. See https://docs.payments.service.gov.uk/testing_govuk_pay/#mock-card-numbers-and-email-addresses for test card numbers")
    private String cardNo;
    @Schema(example = "Joe B", description = "Cardholder name")
    private String cardHolder;
    @Schema(example = "123")
    private String cvc;
    @Schema(example = "01/99")
    private CardExpiryDate endDate;
    private Address address;
    @Schema(example = "visa")
    private String cardBrand;
    @Schema(example = "Mozilla/5.0")
    private String userAgentHeader;
    @Schema(example = "text/html")
    private String acceptHeader;
    @Schema(example = "DEBIT")
    private PayersCardType payersCardType;
    @Schema(example = "NOT_PREPAID")
    private PayersCardPrepaidStatus payersCardPrepaidStatus;
    @Schema(example = "false")
    private Boolean corporateCard;
    @Schema(example = "1f1154b7-620d-4654-801b-893b5bb22db1")
    private String worldpay3dsFlexDdcResult;
    @Schema(example = "127.0.0.1")
    private String ipAddress;
    @Schema(example = "24")
    private String jsScreenColorDepth;
    @Schema(example = "en-GB")
    private String jsNavigatorLanguage;
    @Schema(example = "900")
    private String jsScreenHeight;
    @Schema(example = "1440")
    private String jsScreenWidth;
    @Schema(example = "-60")
    private String jsTimezoneOffsetMins;
    @Schema(example = "fr;q=0.9, fr-CH;q=1.0, en;q=0.8, de;q=0.7, *;q=0.5")
    private String acceptLanguageHeader;

    public static AuthCardDetails anAuthCardDetails() {
        return new AuthCardDetails();
    }

    public static AuthCardDetails of(AuthoriseRequest authoriseRequest, ChargeEntity chargeEntity, CardInformation cardInformation) {
        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo(authoriseRequest.getCardNumber());
        authCardDetails.setCardHolder(authoriseRequest.getCardholderName());
        authCardDetails.setEndDate(CardExpiryDate.valueOf(authoriseRequest.getExpiryDate()));
        authCardDetails.setCvc(authoriseRequest.getCvc());

        authCardDetails.setCardBrand(cardInformation.getBrand());
        authCardDetails.setCorporateCard(cardInformation.isCorporate());
        authCardDetails.setPayersCardType(CardidCardType.toPayersCardType(cardInformation.getType()));
        authCardDetails.setPayersCardPrepaidStatus(cardInformation.getPrepaidStatus());

        Optional.ofNullable(chargeEntity.getChargeCardDetails())
                .flatMap(chargeCardDetailsEntity -> chargeCardDetailsEntity.getBillingAddress())
                .map(Address::from)
                .ifPresent(authCardDetails::setAddress);

        return authCardDetails;
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

    @JsonProperty("accept_language_header")
    public void setAcceptLanguageHeader(String acceptLanguageHeader) {
        this.acceptLanguageHeader = acceptLanguageHeader;
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

    public String getAcceptLanguageHeader() {
        return acceptLanguageHeader;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthCardDetails that = (AuthCardDetails) o;
        return Objects.equals(cardNo, that.cardNo) && Objects.equals(cardHolder, that.cardHolder) && Objects.equals(cvc, that.cvc) && Objects.equals(endDate, that.endDate) && Objects.equals(address, that.address) && Objects.equals(cardBrand, that.cardBrand) && Objects.equals(userAgentHeader, that.userAgentHeader) && Objects.equals(acceptHeader, that.acceptHeader) && payersCardType == that.payersCardType && payersCardPrepaidStatus == that.payersCardPrepaidStatus && Objects.equals(corporateCard, that.corporateCard) && Objects.equals(worldpay3dsFlexDdcResult, that.worldpay3dsFlexDdcResult) && Objects.equals(ipAddress, that.ipAddress) && Objects.equals(jsScreenColorDepth, that.jsScreenColorDepth) && Objects.equals(jsNavigatorLanguage, that.jsNavigatorLanguage) && Objects.equals(jsScreenHeight, that.jsScreenHeight) && Objects.equals(jsScreenWidth, that.jsScreenWidth) && Objects.equals(jsTimezoneOffsetMins, that.jsTimezoneOffsetMins) && Objects.equals(acceptLanguageHeader, that.acceptLanguageHeader);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardNo, cardHolder, cvc, endDate, address, cardBrand, userAgentHeader, acceptHeader, payersCardType, payersCardPrepaidStatus, corporateCard, worldpay3dsFlexDdcResult, ipAddress, jsScreenColorDepth, jsNavigatorLanguage, jsScreenHeight, jsScreenWidth, jsTimezoneOffsetMins, acceptLanguageHeader);
    }
}
