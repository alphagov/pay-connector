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
        entity.setCorporateDebitCardSurchargeAmount(200L);
        entity.setCorporateCreditCardSurchargeAmount(300L);
        entity.setCorporatePrepaidDebitCardSurchargeAmount(500L);
        entity.setAllowApplePay(false);
        entity.setAllowGooglePay(true);
        entity.setEmailCollectionMode(EmailCollectionMode.MANDATORY);
        entity.setRequires3ds(true);
        entity.setAllowZeroAmount(true);
        entity.setIntegrationVersion3ds(2);
        entity.setBlockPrepaidCards(true);
        entity.setAllowMoto(true);
        entity.setMotoMaskCardNumberInput(true);
        entity.setMotoMaskCardSecurityCodeInput(true);
        entity.setAllowTelephonePaymentNotifications(true);
        entity.setProviderSwitchEnabled(true);
        entity.setSendPayerEmailToGateway(true);
        entity.setSendPayerIpAddressToGateway(true);
        entity.setSendReferenceToGateway(true);
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
        
        GatewayAccountResponse dto = GatewayAccountResponse.of(entity);
        assertThat(dto.accountId(), is(entity.getId()));
        assertThat(dto.externalId(), is(entity.getExternalId()));
        assertThat(dto.paymentProvider(), is(entity.getGatewayName()));
        assertThat(dto.type().toString(), is(entity.getType()));
        assertThat(dto.isLive(), is(entity.isLive()));
        assertThat(dto.description(), is(entity.getDescription()));
        assertThat(dto.serviceName(), is(entity.getServiceName()));
        assertThat(dto.analyticsId(), is(entity.getAnalyticsId()));
        assertThat(dto.corporateCreditCardSurchargeAmount(), is(entity.getCorporateNonPrepaidCreditCardSurchargeAmount()));
        assertThat(dto.corporateDebitCardSurchargeAmount(), is(entity.getCorporateNonPrepaidDebitCardSurchargeAmount()));
        assertThat(dto.corporatePrepaidDebitCardSurchargeAmount(), is(entity.getCorporatePrepaidDebitCardSurchargeAmount()));
        assertThat(dto.isAllowApplePay(), is(entity.isAllowApplePay()));
        assertThat(dto.isAllowGooglePay(), is(entity.isAllowGooglePay()));
        assertThat(dto.isRequires3ds(), is(entity.isRequires3ds()));
        assertThat(dto.isAllowZeroAmount(), is(entity.isAllowZeroAmount()));
        assertThat(dto.emailCollectionMode(), is(entity.getEmailCollectionMode()));
        assertThat(dto.emailNotifications().size(), is(1));
        assertThat(dto.emailNotifications().get(EmailNotificationType.PAYMENT_CONFIRMED).getTemplateBody(), is("testTemplate"));
        assertThat(dto.integrationVersion3ds(), is(entity.getIntegrationVersion3ds()));
        assertThat(dto.isBlockPrepaidCards(), is(entity.isBlockPrepaidCards()));
        assertThat(dto.isAllowMoto(), is(entity.isAllowMoto()));
        assertThat(dto.isMotoMaskCardNumberInput(), is(entity.isMotoMaskCardNumberInput()));
        assertThat(dto.isMotoMaskCardSecurityCodeInput(), is(entity.isMotoMaskCardSecurityCodeInput()));
        assertThat(dto.isAllowTelephonePaymentNotifications(), is(entity.isAllowTelephonePaymentNotifications()));
        assertThat(dto.worldpay3dsFlexCredentials(), is(entity.getWorldpay3dsFlexCredentials().get()));
        assertThat(dto.isProviderSwitchEnabled(), is(entity.isProviderSwitchEnabled()));
        assertThat(dto.isSendPayerIpAddressToGateway(), is(true));
        assertThat(dto.isSendPayerEmailToGateway(), is(true));
        assertThat(dto.isSendReferenceToGateway(), is(true));
        assertThat(dto.isAllowAuthorisationApi(), is(entity.isAllowAuthorisationApi()));
        assertThat(dto.isRecurringEnabled(), is(entity.isRecurringEnabled()));
        assertThat(dto.isDisabled(), is(entity.isDisabled()));
        assertThat(dto.disabledReason(), is(entity.getDisabledReason()));
    }
}
