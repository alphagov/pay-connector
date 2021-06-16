package uk.gov.pay.connector.gatewayaccountcredentials.service;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchRequest;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountCredentialsRequestValidator.FIELD_CREDENTIALS;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountCredentialsRequestValidator.FIELD_LAST_UPDATED_BY_USER;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountCredentialsRequestValidator.FIELD_STATE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ENTERED;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

public class GatewayAccountCredentialsService {

    private final GatewayAccountCredentialsDao gatewayAccountCredentialsDao;

    @Inject
    public GatewayAccountCredentialsService(GatewayAccountCredentialsDao gatewayAccountCredentialsDao) {
        this.gatewayAccountCredentialsDao = gatewayAccountCredentialsDao;
    }

    public Optional<GatewayAccountCredentialsEntity> getGatewayAccountCredentials(long id) {
        return gatewayAccountCredentialsDao.findById(id);
    }

    @Transactional
    public GatewayAccountCredentials createGatewayAccountCredentials(GatewayAccountEntity gatewayAccountEntity, String paymentProvider,
                                                                     Map<String, String> credentials) {
        GatewayAccountCredentialState state = calculateStateForNewCredentials(gatewayAccountEntity, paymentProvider, credentials);
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity
                = new GatewayAccountCredentialsEntity(gatewayAccountEntity, paymentProvider, credentials, state);

        if (state == ACTIVE) {
            gatewayAccountCredentialsEntity.setActiveStartDate(Instant.now());
        }
        gatewayAccountCredentialsEntity.setExternalId(randomUuid());

        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntity);
        return new GatewayAccountCredentials(gatewayAccountCredentialsEntity);
    }

    private GatewayAccountCredentialState calculateStateForNewCredentials(GatewayAccountEntity gatewayAccountEntity,
                                                                          String paymentProvider, Map<String, String> credentials) {
        PaymentGatewayName paymentGatewayName = PaymentGatewayName.valueFrom(paymentProvider);
        boolean isFirstCredentials = !gatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountEntity.getId());
        boolean credentialsPrePopulated = !credentials.isEmpty();

        if ((isFirstCredentials && credentialsPrePopulated) || paymentGatewayName == SANDBOX) {
            return ACTIVE;
        }

        return credentialsPrePopulated ? ENTERED : CREATED;
    }

    /**
     * This method is for updating the gateway_account_credentials table when the old endpoint for patching
     * credentials is called. This is a temporary measure while we completely switch over to using the
     * gateway_account_credentials table for storing and getting credentials.
     */
    @Transactional
    public void updateGatewayAccountCredentialsForLegacyEndpoint(GatewayAccountEntity gatewayAccountEntity,
                                                                 Map<String, String> credentials) {
        List<GatewayAccountCredentialsEntity> credentialsEntities = gatewayAccountEntity.getGatewayAccountCredentials();
        if (credentialsEntities.isEmpty()) {
            // Backfill hasn't been run for this gateway account, no need to add to gateway_account_credentials table
            // as row will be added when backfill is run.
            return;
        }
        if (credentialsEntities.size() > 1) {
            throw new WebApplicationException(serviceErrorResponse(
                    format("Expected exactly one gateway_account_credentials entity, found %s",
                            credentialsEntities.size())));
        }

        var credentialsEntity = credentialsEntities.get(0);
        credentialsEntity.setCredentials(credentials);
        if (credentialsEntity.getState() != ACTIVE) {
            credentialsEntity.setState(ACTIVE);
            credentialsEntity.setActiveStartDate(Instant.now());
        }
    }

    @Transactional
    public GatewayAccountCredentials updateGatewayAccountCredentials(long gatewayAccountCredentialsId, List<JsonPatchRequest> updateRequests) {
        return getGatewayAccountCredentials(gatewayAccountCredentialsId).map(
                gatewayAccountCredentialsEntity -> {
                    for (JsonPatchRequest updateRequest : updateRequests) {
                        if (updateRequest.getPath().equals(FIELD_CREDENTIALS) && updateRequest.getOp() == JsonPatchOp.REPLACE) {
                            gatewayAccountCredentialsEntity.setCredentials(updateRequest.valueAsObject());
                            if (gatewayAccountCredentialsEntity.getState() == CREATED) {
                                updateStateForEnteredCredentials(gatewayAccountCredentialsEntity);
                            }
                        } else if (updateRequest.getPath().equals(FIELD_LAST_UPDATED_BY_USER) && updateRequest.getOp() == JsonPatchOp.REPLACE) {
                            gatewayAccountCredentialsEntity.setLastUpdatedByUserExternalId(updateRequest.valueAsString());
                        } else if (updateRequest.getPath().equals(FIELD_STATE) && updateRequest.getOp() == JsonPatchOp.REPLACE) {
                            gatewayAccountCredentialsEntity.setState(GatewayAccountCredentialState.valueOf(updateRequest.valueAsString()));
                        }
                    }

                    return new GatewayAccountCredentials(gatewayAccountCredentialsEntity);
                }).orElseThrow(() -> new GatewayAccountCredentialsNotFoundException(gatewayAccountCredentialsId));
    }
    
    private void updateStateForEnteredCredentials(GatewayAccountCredentialsEntity credentialsEntity) {
        if (credentialsEntity.getGatewayAccountEntity().getGatewayAccountCredentials().size() == 1) {
            credentialsEntity.setState(ACTIVE);
            credentialsEntity.setActiveStartDate(Instant.now());
        } else {
            credentialsEntity.setState(ENTERED);
        }
    }

}
