package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.persist.Transactional;
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
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;

public class GatewayAccountSwitchPaymentProviderService {

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
                .orElseThrow(() -> new BadRequestException(format("Account credential with ACTIVE state not found.", request.getGACredentialExternalId())));

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

        gatewayAccountCredentialsDao.merge(switchToCredentialsEntity);
        gatewayAccountCredentialsDao.merge(activeCredentialEntity);
        gatewayAccountDao.merge(gatewayAccountEntity);
    }
}
