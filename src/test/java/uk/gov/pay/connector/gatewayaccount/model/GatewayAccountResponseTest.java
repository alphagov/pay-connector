package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;

class GatewayAccountResponseTest {

    @Test
    void fromEntity() {
        GatewayAccountEntity entity = new GatewayAccountEntity();
        entity.setId(100L);
        entity.setExternalId("some-external-id");
        entity.setType(GatewayAccountType.fromString("test"));
        entity.setDescription("aDescription");
        entity.setServiceName("aServiceName");
        entity.setAnalyticsId("123");
        entity.getCardConfigurationEntity().setCorporateDebitCardSurchargeAmount(200L);
        entity.getCardConfigurationEntity().setCorporateCreditCardSurchargeAmount(300L);
        entity.getCardConfigurationEntity().setCorporatePrepaidDebitCardSurchargeAmount(500L);
        entity.getCardConfigurationEntity().setAllowApplePay(false);
        entity.getCardConfigurationEntity().setAllowGooglePay(true);
        entity.setEmailCollectionMode(EmailCollectionMode.MANDATORY);
        entity.getCardConfigurationEntity().setRequires3ds(true);
        entity.setAllowZeroAmount(true);
        entity.getCardConfigurationEntity().setIntegrationVersion3ds(2);
        entity.getCardConfigurationEntity().setBlockPrepaidCards(true);
        entity.setAllowMoto(true);
        entity.getCardConfigurationEntity().setMotoMaskCardNumberInput(true);
        entity.getCardConfigurationEntity().setMotoMaskCardSecurityCodeInput(true);
        entity.setAllowTelephonePaymentNotifications(true);
        entity.setProviderSwitchEnabled(true);
        entity.getCardConfigurationEntity().setSendPayerEmailToGateway(true);
        entity.getCardConfigurationEntity().setSendPayerIpAddressToGateway(true);
        entity.getCardConfigurationEntity().setSendReferenceToGateway(true);
        entity.setAllowAuthorisationApi(true);
        entity.setRecurringEnabled(true);
        entity.setDisabled(true);
        entity.setDisabledReason("Disabled because of reasons");
        entity.setWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build());
        entity.setGatewayAccountCredentials(List.of(
                GatewayAccountCredentialsEntityFixture.
                        aGatewayAccountCredentialsEntity()
                        .withPaymentProvider(SANDBOX.getName())
                        .withState(GatewayAccountCredentialState.ACTIVE)
                        .build()
        ));

        Map<EmailNotificationType, EmailNotificationEntity> emailNotifications = new HashMap<>();
        emailNotifications.put(EmailNotificationType.PAYMENT_CONFIRMED, new EmailNotificationEntity(new GatewayAccountEntity(), "testTemplate", true));
        entity.setEmailNotifications(emailNotifications);
        
        GatewayAccountResponse dto = new GatewayAccountResponse(entity);
        assertThat(dto.getAccountId(), is(entity.getId()));
        assertThat(dto.getExternalId(), is(entity.getExternalId()));
        assertThat(dto.getPaymentProvider(), is(entity.getGatewayName()));
        assertThat(dto.getType(), is(entity.getType()));
        assertThat(dto.isLive(), is(entity.isLive()));
        assertThat(dto.getDescription(), is(entity.getDescription()));
        assertThat(dto.getServiceName(), is(entity.getServiceName()));
        assertThat(dto.getAnalyticsId(), is(entity.getAnalyticsId()));
        assertThat(dto.getCorporateCreditCardSurchargeAmount(), is(entity.getCardConfigurationEntity().getCorporateNonPrepaidCreditCardSurchargeAmount()));
        assertThat(dto.getCorporateDebitCardSurchargeAmount(), is(entity.getCardConfigurationEntity().getCorporateNonPrepaidDebitCardSurchargeAmount()));
        assertThat(dto.getCorporatePrepaidDebitCardSurchargeAmount(), is(entity.getCardConfigurationEntity().getCorporatePrepaidDebitCardSurchargeAmount()));
        assertThat(dto.isAllowApplePay(), is(entity.getCardConfigurationEntity().isAllowApplePay()));
        assertThat(dto.isAllowGooglePay(), is(entity.getCardConfigurationEntity().isAllowGooglePay()));
        assertThat(dto.isRequires3ds(), is(entity.getCardConfigurationEntity().isRequires3ds()));
        assertThat(dto.isAllowZeroAmount(), is(entity.isAllowZeroAmount()));
        assertThat(dto.getEmailCollectionMode(), is(entity.getEmailCollectionMode()));
        assertThat(dto.getEmailNotifications().size(), is(1));
        assertThat(dto.getEmailNotifications().get(EmailNotificationType.PAYMENT_CONFIRMED).getTemplateBody(), is("testTemplate"));
        assertThat(dto.getIntegrationVersion3ds(), is(entity.getCardConfigurationEntity().getIntegrationVersion3ds()));
        assertThat(dto.isBlockPrepaidCards(), is(entity.getCardConfigurationEntity().isBlockPrepaidCards()));
        assertThat(dto.isAllowMoto(), is(entity.isAllowMoto()));
        assertThat(dto.isMotoMaskCardNumberInput(), is(entity.getCardConfigurationEntity().isMotoMaskCardNumberInput()));
        assertThat(dto.isMotoMaskCardSecurityCodeInput(), is(entity.getCardConfigurationEntity().isMotoMaskCardSecurityCodeInput()));
        assertThat(dto.isAllowTelephonePaymentNotifications(), is(entity.isAllowTelephonePaymentNotifications()));
        assertThat(dto.getWorldpay3dsFlexCredentials(), is(entity.getWorldpay3dsFlexCredentials()));
        assertThat(dto.isProviderSwitchEnabled(), is(entity.isProviderSwitchEnabled()));
        assertThat(dto.isSendPayerIpAddressToGateway(), is(true));
        assertThat(dto.isSendPayerEmailToGateway(), is(true));
        assertThat(dto.isSendReferenceToGateway(), is(true));
        assertThat(dto.isAllowAuthorisationApi(), is(entity.isAllowAuthorisationApi()));
        assertThat(dto.isRecurringEnabled(), is(entity.isRecurringEnabled()));
        assertThat(dto.isDisabled(), is(entity.isDisabled()));
        assertThat(dto.getDisabledReason(), is(entity.getDisabledReason()));
    }
}
