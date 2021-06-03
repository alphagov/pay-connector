package uk.gov.pay.connector.gatewayaccountcredentials.service;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Map;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ENTERED;

public class GatewayAccountCredentialsService {

    private final GatewayAccountCredentialsDao gatewayAccountCredentialsDao;

    @Inject
    public GatewayAccountCredentialsService(GatewayAccountCredentialsDao gatewayAccountCredentialsDao) {
        this.gatewayAccountCredentialsDao = gatewayAccountCredentialsDao;
    }

    @Transactional
    public void createGatewayAccountCredentials(GatewayAccountEntity gatewayAccountEntity, String paymentProvider,
                                                Map<String, String> credentials) {
        GatewayAccountCredentialState state = calculateState(gatewayAccountEntity, paymentProvider, credentials);
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity
                = new GatewayAccountCredentialsEntity(gatewayAccountEntity, paymentProvider, credentials, state);

        if (state == ACTIVE) {
            gatewayAccountCredentialsEntity.setActiveStartDate(Instant.now());
        }

        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntity);
    }

    private GatewayAccountCredentialState calculateState(GatewayAccountEntity gatewayAccountEntity,
                                                         String paymentProvider, Map<String, String> credentials) {
        PaymentGatewayName paymentGatewayName = PaymentGatewayName.valueFrom(paymentProvider);
        boolean isFirstCredentials = !gatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountEntity.getId());
        boolean credentialsPrePopulated = !credentials.isEmpty();

        if ((isFirstCredentials && credentialsPrePopulated) || paymentGatewayName == SANDBOX) {
            return ACTIVE;
        }

        return credentialsPrePopulated ? ENTERED : CREATED;
    }

}
