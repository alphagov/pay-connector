package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.Test;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;


public class GatewayAccountResourceDTOTest {

    @Test
    public void fromEntity() {
        GatewayAccountEntity entity = new GatewayAccountEntity();
        entity.setId(100L);
        entity.setGatewayName("testGatewayName");
        entity.setType(GatewayAccountEntity.Type.fromString("test"));
        entity.setDescription("aDescription");
        entity.setServiceName("aServiceName");
        entity.setAnalyticsId("123");
        entity.setCorporateDebitCardSurchargeAmount(200L);
        entity.setCorporateCreditCardSurchargeAmount(300L);
        entity.setCorporatePrepaidCreditCardSurchargeAmount(400L);
        entity.setCorporatePrepaidDebitCardSurchargeAmount(500L);
        entity.setAllowApplePay(false);
        entity.setAllowGooglePay(true);
        entity.setCredentials(Collections.emptyMap());
        entity.setEmailCollectionMode(EmailCollectionMode.MANDATORY);
        entity.setRequires3ds(true);
        entity.setAllowZeroAmount(true);

        Map<EmailNotificationType, EmailNotificationEntity> emailNotifications = new HashMap<>();
        emailNotifications.put(EmailNotificationType.PAYMENT_CONFIRMED, new EmailNotificationEntity(new GatewayAccountEntity(), "testTemplate", true));
        entity.setEmailNotifications(emailNotifications);
        
        GatewayAccountResourceDTO dto = GatewayAccountResourceDTO.fromEntity(entity);
        assertThat(dto.getAccountId(), is(entity.getId()));
        assertThat(dto.getPaymentProvider(), is(entity.getGatewayName()));
        assertThat(dto.getType(), is(entity.getType()));
        assertThat(dto.getDescription(), is(entity.getDescription()));
        assertThat(dto.getServiceName(), is(entity.getServiceName()));
        assertThat(dto.getAnalyticsId(), is(entity.getAnalyticsId()));
        assertThat(dto.getCorporateCreditCardSurchargeAmount(), is(entity.getCorporateNonPrepaidCreditCardSurchargeAmount()));
        assertThat(dto.getCorporateDebitCardSurchargeAmount(), is(entity.getCorporateNonPrepaidDebitCardSurchargeAmount()));
        assertThat(dto.getCorporatePrepaidCreditCardSurchargeAmount(), is(entity.getCorporatePrepaidCreditCardSurchargeAmount()));
        assertThat(dto.getCorporatePrepaidDebitCardSurchargeAmount(), is(entity.getCorporatePrepaidDebitCardSurchargeAmount()));
        assertThat(dto.isAllowApplePay(), is(entity.isAllowApplePay()));
        assertThat(dto.isAllowGooglePay(), is(entity.isAllowGooglePay()));
        assertThat(dto.isRequires3ds(), is(entity.isRequires3ds()));
        assertThat(dto.isAllowZeroAmount(), is(entity.isAllowZeroAmount()));
        assertThat(dto.getEmailCollectionMode(), is(entity.getEmailCollectionMode()));
        assertThat(dto.getEmailNotifications().size(), is(1));
        assertThat(dto.getEmailNotifications().get(EmailNotificationType.PAYMENT_CONFIRMED).getTemplateBody(), is("testTemplate"));
    }
}
