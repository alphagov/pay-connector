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
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
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
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

        if (state == ACTIVE) {
            gatewayAccountCredentialsEntity
                    = new GatewayAccountCredentialsEntity(gatewayAccountEntity, paymentProvider, credentials,
                    state, Instant.now());
        } else {
            gatewayAccountCredentialsEntity
                    = new GatewayAccountCredentialsEntity(gatewayAccountEntity, paymentProvider, credentials, state);
        }

        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntity);
    }

    private GatewayAccountCredentialState calculateState(GatewayAccountEntity gatewayAccountEntity,
                                                         String paymentProvider, Map<String, String> credentials) {
        PaymentGatewayName paymentGatewayName = PaymentGatewayName.valueFrom(paymentProvider);
        if (paymentGatewayName == SANDBOX) {
            return ACTIVE;
        }
        if (paymentGatewayName == STRIPE && !credentials.isEmpty()) {
            boolean hasActiveCredentials =
                    gatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountEntity.getId());
            if (hasActiveCredentials){
                return ENTERED;
            }
            return ACTIVE;
        }

        return CREATED;
    }
}
