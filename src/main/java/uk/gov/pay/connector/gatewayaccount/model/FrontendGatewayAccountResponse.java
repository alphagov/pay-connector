package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;

import java.util.List;

@Schema(description = "Representation of a gateway account for use by the card frontend application")
public record FrontendGatewayAccountResponse (

    @JsonProperty("gateway_account_id")
    @Schema(example = "1", description = "The account ID")
    long id,

    @JsonProperty("external_id")
    @Schema(example = "fbf905a3f7ea416c8c252410eb45ddbd", description = "External ID for the gateway account")
    String externalId,
    
    @JsonProperty("payment_provider")
    @Schema(example = "sandbox", description = "The payment provider for which this account is created")
    String paymentProvider,

    @JsonProperty("gateway_merchant_id")
    @Schema(example = "abc123", description = "Google Pay merchant ID for Worldpay accounts")
    String gatewayMerchantId,

    @JsonProperty("type")
    @Schema(example = "test", description = "Account type for the payment provider (test/live)")
    String type,

    @JsonProperty("service_name")
    @Schema(example = "service name", description = "The service name for the account")
    String serviceName,

    @JsonProperty("service_id")
    @Schema(example = "cd1b871207a94a7fa157dee678146acd", description = "Service external ID")
    String serviceId,

    @JsonProperty("analytics_id")
    @Schema(description = "An identifier used to identify the service in Google Analytics. The default value is null")
    String analyticsId,

    @JsonProperty("corporate_credit_card_surcharge_amount")
    long corporateCreditCardSurchargeAmount,

    @JsonProperty("corporate_debit_card_surcharge_amount")
    long corporateDebitCardSurchargeAmount,

    @JsonProperty("corporate_prepaid_debit_card_surcharge_amount")
    long corporatePrepaidDebitCardSurchargeAmount,

    @JsonProperty("allow_apple_pay")
    @Schema(example = "true", description = "Set to true to enable Apple Pay", defaultValue = "false")
    boolean allowApplePay,

    @JsonProperty("allow_google_pay")
    @Schema(example = "true", description = "Set to true to enable Google Pay", defaultValue = "false")
    boolean allowGooglePay,
    
    @JsonProperty("block_prepaid_cards")
    @Schema(example = "true", description = "Whether pre-paid cards are allowed as a payment method for this gateway account", defaultValue = "false")
    boolean blockPrepaidCards,

    @JsonProperty("email_collection_mode")
    @Schema(description = "Whether email address is required from paying users. Can be MANDATORY, OPTIONAL or OFF")
    EmailCollectionMode emailCollectionMode,

    @JsonProperty("requires3ds")
    @Schema(example = "true", description = "Flag to indicate whether 3DS is enabled")
    boolean requires3ds,

    @JsonProperty("integration_version_3ds")
    @Schema(example = "2", description = "3DS version used for payments for the gateway account")
    int integrationVersion3ds,

    @JsonProperty("moto_mask_card_number_input")
    @Schema(description = "Indicates whether the card number is masked when being input for MOTO payments. The default value is false.", defaultValue = "false")
    boolean motoMaskCardNumberInput,

    @JsonProperty("moto_mask_card_security_code_input")
    @Schema(description = "Indicates whether the card security code is masked when being input for MOTO payments.", defaultValue = "false")
    boolean motoMaskCardSecurityCodeInput,

    @JsonProperty("card_types")
    @Schema(description = "The supported card types for the account")
    List<CardTypeEntity> cardTypes
){

    public FrontendGatewayAccountResponse(GatewayAccountEntity gatewayAccountEntity) {
        this(
                gatewayAccountEntity.getId(),
                gatewayAccountEntity.getExternalId(),
                gatewayAccountEntity.getGatewayName(),
                gatewayAccountEntity.getGooglePayMerchantId(),
                gatewayAccountEntity.getType(),
                gatewayAccountEntity.getServiceName(),
                gatewayAccountEntity.getServiceId(),
                gatewayAccountEntity.getAnalyticsId(),
                gatewayAccountEntity.getCorporateNonPrepaidCreditCardSurchargeAmount(),
                gatewayAccountEntity.getCorporateNonPrepaidCreditCardSurchargeAmount(),
                gatewayAccountEntity.getCorporatePrepaidDebitCardSurchargeAmount(),
                gatewayAccountEntity.isAllowApplePay(),
                gatewayAccountEntity.isAllowGooglePay(),
                gatewayAccountEntity.isBlockPrepaidCards(),
                gatewayAccountEntity.getEmailCollectionMode(),
                gatewayAccountEntity.isRequires3ds(),
                gatewayAccountEntity.getIntegrationVersion3ds(),
                gatewayAccountEntity.isMotoMaskCardNumberInput(),
                gatewayAccountEntity.isMotoMaskCardSecurityCodeInput(),
                gatewayAccountEntity.getCardTypes()
        );
    }
}
