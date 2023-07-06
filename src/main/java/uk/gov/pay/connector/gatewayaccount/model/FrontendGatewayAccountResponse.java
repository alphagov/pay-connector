package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;

import java.util.List;

@Schema(description = "Representation of a gateway account for use by the card frontend application")
public class FrontendGatewayAccountResponse {

    @JsonProperty("gateway_account_id")
    @Schema(example = "1", description = "The account ID")
    private final long id;

    @JsonProperty("external_id")
    @Schema(example = "fbf905a3f7ea416c8c252410eb45ddbd", description = "External ID for the gateway account")
    private final String externalId;
    
    @JsonProperty("payment_provider")
    @Schema(example = "sandbox", description = "The payment provider for which this account is created")
    private final String paymentProvider;

    @JsonProperty("gateway_merchant_id")
    @Schema(example = "abc123", description = "Google Pay merchant ID for Worldpay accounts")
    private final String gatewayMerchantId;

    @JsonProperty("type")
    @Schema(example = "test", description = "Account type for the payment provider (test/live)")
    private final String type;

    @JsonProperty("service_name")
    @Schema(example = "service name", description = "The service name for the account")
    private final String serviceName;

    @JsonProperty("service_id")
    @Schema(example = "cd1b871207a94a7fa157dee678146acd", description = "Service external ID")
    private final String serviceId;

    @JsonProperty("analytics_id")
    @Schema(description = "An identifier used to identify the service in Google Analytics. The default value is null")
    private final String analyticsId;

    @JsonProperty("corporate_credit_card_surcharge_amount")
    private final long corporateCreditCardSurchargeAmount;

    @JsonProperty("corporate_debit_card_surcharge_amount")
    private final long corporateDebitCardSurchargeAmount;

    @JsonProperty("corporate_prepaid_debit_card_surcharge_amount")
    private final long corporatePrepaidDebitCardSurchargeAmount;

    @JsonProperty("allow_apple_pay")
    @Schema(example = "true", description = "Set to true to enable Apple Pay", defaultValue = "false")
    private final boolean allowApplePay;

    @JsonProperty("allow_google_pay")
    @Schema(example = "true", description = "Set to true to enable Google Pay", defaultValue = "false")
    private final boolean allowGooglePay;
    
    @JsonProperty("block_prepaid_cards")
    @Schema(example = "true", description = "Whether pre-paid cards are allowed as a payment method for this gateway account", defaultValue = "false")
    private final boolean blockPrepaidCards;

    @JsonProperty("email_collection_mode")
    @Schema(description = "Whether email address is required from paying users. Can be MANDATORY, OPTIONAL or OFF")
    private final EmailCollectionMode emailCollectionMode;

    @JsonProperty("requires3ds")
    @Schema(example = "true", description = "Flag to indicate whether 3DS is enabled")
    private final boolean requires3ds;

    @JsonProperty("integration_version_3ds")
    @Schema(example = "2", description = "3DS version used for payments for the gateway account")
    private final int integrationVersion3ds;

    @JsonProperty("moto_mask_card_number_input")
    @Schema(description = "Indicates whether the card number is masked when being input for MOTO payments. The default value is false.", defaultValue = "false")
    private final boolean motoMaskCardNumberInput;

    @JsonProperty("moto_mask_card_security_code_input")
    @Schema(description = "Indicates whether the card security code is masked when being input for MOTO payments.", defaultValue = "false")
    private final boolean motoMaskCardSecurityCodeInput;

    @JsonProperty("card_types")
    @Schema(description = "The supported card types for the account")
    private final List<CardTypeEntity> cardTypes;

    public FrontendGatewayAccountResponse(GatewayAccountEntity gatewayAccountEntity) {
        this.id = gatewayAccountEntity.getId();
        this.externalId = gatewayAccountEntity.getExternalId();
        this.paymentProvider = gatewayAccountEntity.getGatewayName();
        this.gatewayMerchantId = gatewayAccountEntity.getGatewayMerchantId();
        this.type = gatewayAccountEntity.getType();
        this.serviceName = gatewayAccountEntity.getServiceName();
        this.serviceId = gatewayAccountEntity.getServiceId();
        this.analyticsId = gatewayAccountEntity.getAnalyticsId();
        this.corporateCreditCardSurchargeAmount = gatewayAccountEntity.getCorporateNonPrepaidCreditCardSurchargeAmount();
        this.corporateDebitCardSurchargeAmount = gatewayAccountEntity.getCorporateNonPrepaidDebitCardSurchargeAmount();
        this.corporatePrepaidDebitCardSurchargeAmount = gatewayAccountEntity.getCorporatePrepaidDebitCardSurchargeAmount();
        this.allowApplePay = gatewayAccountEntity.isAllowApplePay();
        this.allowGooglePay = gatewayAccountEntity.isAllowGooglePay();
        this.blockPrepaidCards = gatewayAccountEntity.isBlockPrepaidCards();
        this.emailCollectionMode = gatewayAccountEntity.getEmailCollectionMode();
        this.requires3ds = gatewayAccountEntity.isRequires3ds();
        this.integrationVersion3ds = gatewayAccountEntity.getIntegrationVersion3ds();
        this.motoMaskCardNumberInput = gatewayAccountEntity.isMotoMaskCardNumberInput();
        this.motoMaskCardSecurityCodeInput = gatewayAccountEntity.isMotoMaskCardSecurityCodeInput();
        this.cardTypes = gatewayAccountEntity.getCardTypes();
    }

    public long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public String getGatewayMerchantId() {
        return gatewayMerchantId;
    }

    public String getType() {
        return type;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getAnalyticsId() {
        return analyticsId;
    }

    public long getCorporateCreditCardSurchargeAmount() {
        return corporateCreditCardSurchargeAmount;
    }

    public long getCorporateDebitCardSurchargeAmount() {
        return corporateDebitCardSurchargeAmount;
    }

    public long getCorporatePrepaidDebitCardSurchargeAmount() {
        return corporatePrepaidDebitCardSurchargeAmount;
    }

    public boolean isAllowApplePay() {
        return allowApplePay;
    }

    public boolean isAllowGooglePay() {
        return allowGooglePay;
    }

    public boolean isBlockPrepaidCards() {
        return blockPrepaidCards;
    }

    public EmailCollectionMode getEmailCollectionMode() {
        return emailCollectionMode;
    }

    public boolean isRequires3ds() {
        return requires3ds;
    }

    public int getIntegrationVersion3ds() {
        return integrationVersion3ds;
    }

    public boolean isMotoMaskCardNumberInput() {
        return motoMaskCardNumberInput;
    }

    public boolean isMotoMaskCardSecurityCodeInput() {
        return motoMaskCardSecurityCodeInput;
    }

    public List<CardTypeEntity> getCardTypes() {
        return cardTypes;
    }
}
