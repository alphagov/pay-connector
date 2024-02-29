package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "gateway_account_card_configuration")
@SequenceGenerator(name = "gateway_account_card_configuration_id_seq",
        sequenceName = "gateway_account_card_configuration_id_seq", allocationSize = 1)
public class GatewayAccountCardConfigurationEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gateway_account_card_configuration_id_seq")
    private Long id;
    
    @JoinColumn(name = "gateway_account_id", updatable = false, insertable = false)
    @JsonIgnore
    private GatewayAccountEntity gatewayAccountEntity;

    @Column(name = "requires_3ds")
    private boolean requires3ds;

    @Column(name = "allow_google_pay")
    private boolean allowGooglePay;

    @Column(name = "allow_apple_pay")
    private boolean allowApplePay;

    @Column(name = "corporate_credit_card_surcharge_amount")
    private long corporateCreditCardSurchargeAmount;

    @Column(name = "corporate_debit_card_surcharge_amount")
    private long corporateDebitCardSurchargeAmount;

    @Column(name = "corporate_prepaid_debit_card_surcharge_amount")
    private long corporatePrepaidDebitCardSurchargeAmount;

    @Column(name = "block_prepaid_cards")
    private boolean blockPrepaidCards;

    @Column(name = "moto_mask_card_number_input")
    private boolean motoMaskCardNumberInput;

    @Column(name = "moto_mask_card_security_code_input")
    private boolean motoMaskCardSecurityCodeInput;

    @Column(name = "integration_version_3ds")
    private int integrationVersion3ds;

    @Column(name = "send_payer_ip_address_to_gateway")
    private boolean sendPayerIpAddressToGateway;

    @Column(name = "send_payer_email_to_gateway")
    private boolean sendPayerEmailToGateway;

    @Column(name = "send_reference_to_gateway")
    private boolean sendReferenceToGateway;

    public boolean isRequires3ds() {
        return requires3ds;
    }

    public boolean isAllowGooglePay() {
        Boolean hasCredentialsConfiguredForGooglePay = gatewayAccountEntity.getCurrentOrActiveGatewayAccountCredential()
                .map(GatewayAccountCredentialsEntity::getCredentialsObject)
                .map(GatewayCredentials::isConfiguredForGooglePayPayments)
                .orElse(false);
        return allowGooglePay && hasCredentialsConfiguredForGooglePay;
    }

    public boolean isAllowApplePay() {
        return allowApplePay;
    }

    public boolean isBlockPrepaidCards() {
        return blockPrepaidCards;
    }

    public boolean isMotoMaskCardNumberInput() {
        return motoMaskCardNumberInput;
    }

    public boolean isMotoMaskCardSecurityCodeInput() {
        return motoMaskCardSecurityCodeInput;
    }

    public long getCorporateNonPrepaidCreditCardSurchargeAmount() {
        return corporateCreditCardSurchargeAmount;
    }

    public long getCorporateNonPrepaidDebitCardSurchargeAmount() {
        return corporateDebitCardSurchargeAmount;
    }

    public long getCorporatePrepaidDebitCardSurchargeAmount() {
        return corporatePrepaidDebitCardSurchargeAmount;
    }

    public int getIntegrationVersion3ds() {
        return integrationVersion3ds;
    }

    public boolean isSendPayerIpAddressToGateway() {
        return sendPayerIpAddressToGateway;
    }

    public boolean isSendPayerEmailToGateway() {
        return sendPayerEmailToGateway;
    }

    public boolean isSendReferenceToGateway() {
        return sendReferenceToGateway;
    }

    public void setRequires3ds(boolean requires3ds) {
        this.requires3ds = requires3ds;
    }

    public void setCorporateCreditCardSurchargeAmount(long corporateCreditCardSurchargeAmount) {
        this.corporateCreditCardSurchargeAmount = corporateCreditCardSurchargeAmount;
    }

    public void setCorporateDebitCardSurchargeAmount(long corporateDebitCardSurchargeAmount) {
        this.corporateDebitCardSurchargeAmount = corporateDebitCardSurchargeAmount;
    }

    public void setCorporatePrepaidDebitCardSurchargeAmount(long corporatePrepaidDebitCardSurchargeAmount) {
        this.corporatePrepaidDebitCardSurchargeAmount = corporatePrepaidDebitCardSurchargeAmount;
    }

    public void setAllowGooglePay(boolean allowGooglePay) {
        this.allowGooglePay = allowGooglePay;
    }

    public void setAllowApplePay(boolean allowApplePay) {
        this.allowApplePay = allowApplePay;
    }

    public void setBlockPrepaidCards(boolean blockPrepaidCards) {
        this.blockPrepaidCards = blockPrepaidCards;
    }

    public void setMotoMaskCardNumberInput(boolean motoMaskCardNumberInput) {
        this.motoMaskCardNumberInput = motoMaskCardNumberInput;
    }

    public void setMotoMaskCardSecurityCodeInput(boolean motoMaskCardSecurityCodeInput) {
        this.motoMaskCardSecurityCodeInput = motoMaskCardSecurityCodeInput;
    }

    public void setIntegrationVersion3ds(int integrationVersion3ds) {
        this.integrationVersion3ds = integrationVersion3ds;
    }

    public void setSendPayerIpAddressToGateway(boolean sendPayerIpAddressToGateway) {
        this.sendPayerIpAddressToGateway = sendPayerIpAddressToGateway;
    }

    public void setSendPayerEmailToGateway(boolean sendPayerEmailToGateway) {
        this.sendPayerEmailToGateway = sendPayerEmailToGateway;
    }

    public void setSendReferenceToGateway(boolean sendReferenceToGateway) {
        this.sendReferenceToGateway = sendReferenceToGateway;
    }
}
