package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.List;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;
import static uk.gov.service.payments.logging.LoggingKeys.USER_EXTERNAL_ID;

public class GatewayAccountSwitchPaymentProviderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAccountSwitchPaymentProviderService.class);

    private final GatewayAccountCredentialsDao gatewayAccountCredentialsDao;
    private final GatewayAccountDao gatewayAccountDao;

    @Inject
    public GatewayAccountSwitchPaymentProviderService(GatewayAccountDao gatewayAccountDao, GatewayAccountCredentialsDao gatewayAccountCredentialsDao) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.gatewayAccountCredentialsDao = gatewayAccountCredentialsDao;
    }

    @Transactional
    public void switchPaymentProviderForAccount(GatewayAccountEntity gatewayAccountEntity,
                                                GatewayAccountSwitchPaymentProviderRequest request) {

        List<GatewayAccountCredentialsEntity> credentials = gatewayAccountEntity.getGatewayAccountCredentials();

        if (credentials.size() < 2) {
            throw new BadRequestException("Account has no credential to switch to/from");
        }

        var activeCredentialEntity = credentials
                .stream()
                .filter(credential -> ACTIVE.equals(credential.getState())).findFirst()
                .orElseThrow(() -> new BadRequestException("Account credential with ACTIVE state not found."));

        var switchToCredentialsEntity = credentials
                .stream()
                .filter(credential -> request.getGACredentialExternalId().equals(credential.getExternalId())).findFirst()
                .orElseThrow(() -> new NotFoundException(format("Account credential with id [%s] not found.", request.getGACredentialExternalId())));

        if (!switchToCredentialsEntity.getState().equals(VERIFIED_WITH_LIVE_PAYMENT)) {
            throw new BadRequestException(format("Credential with id [%s] is not in correct state.", request.getGACredentialExternalId()));
        }

        switchToCredentialsEntity.setLastUpdatedByUserExternalId(request.getUserExternalId());
        switchToCredentialsEntity.setActiveStartDate(Instant.now());
        switchToCredentialsEntity.setState(ACTIVE);

        activeCredentialEntity.setState(RETIRED);
        activeCredentialEntity.setLastUpdatedByUserExternalId(request.getUserExternalId());
        activeCredentialEntity.setActiveEndDate(Instant.now());

        gatewayAccountEntity.setProviderSwitchEnabled(false);
        gatewayAccountEntity
                .setIntegrationVersion3ds(2);

        gatewayAccountCredentialsDao.merge(switchToCredentialsEntity);
        gatewayAccountCredentialsDao.merge(activeCredentialEntity);
        gatewayAccountDao.merge(gatewayAccountEntity);

        LOGGER.info("Gateway account [id={}] switched to new payment provider", gatewayAccountEntity.getId(),
                kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                kv(PROVIDER, activeCredentialEntity.getPaymentProvider()),
                kv("new_payment_provider", switchToCredentialsEntity.getPaymentProvider()),
                kv(USER_EXTERNAL_ID, request.getUserExternalId())
        );
    }
}
