package uk.gov.pay.connector.service;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.AuthorisationDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.persistence.OptimisticLockException;
import java.util.List;
import java.util.function.Supplier;

import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus;

public abstract class CardAuthoriseBaseService<T extends AuthorisationDetails> extends CardService<BaseAuthoriseResponse> {

    private final CardExecutorService cardExecutorService;
    private final AWSXRayRecorder recorder = AWSXRay.getGlobalRecorder();


    public CardAuthoriseBaseService(ChargeDao chargeDao, PaymentProviders providers, CardExecutorService cardExecutorService, Environment environment) {
        super(chargeDao, providers, environment);
        this.cardExecutorService = cardExecutorService;
    }


    public GatewayResponse doAuthorise(String chargeId, T gatewayAuthRequest) {
        return chargeDao.findByExternalId(chargeId).map(chargeEntity -> {
            Supplier<GatewayResponse> authorisationSupplier = () -> {
                recorder.beginSegment("pay-connector");
                //segment.putService("component","authorisation-request");
                ChargeEntity preOperationResponse;
                try {

                    try {
                        preOperationResponse = preOperation(chargeEntity, gatewayAuthRequest);
                        if (preOperationResponse.hasStatus(ChargeStatus.AUTHORISATION_ABORTED)) {
                            throw new ConflictRuntimeException(chargeEntity.getExternalId(), "configuration mismatch");
                        }
                    } catch (OptimisticLockException e) {
                        throw new ConflictRuntimeException(chargeEntity.getExternalId());
                    }

                    GatewayResponse<BaseAuthoriseResponse> operationResponse = operation(preOperationResponse, gatewayAuthRequest);

                    return postOperation(preOperationResponse, gatewayAuthRequest, operationResponse);

                } finally {
                    recorder.endSegment();
                }
            };

            Pair<ExecutionStatus, GatewayResponse> executeResult = cardExecutorService.execute(authorisationSupplier);

            switch (executeResult.getLeft()) {
                case COMPLETED:
                    return executeResult.getRight();
                case IN_PROGRESS:
                    throw new OperationAlreadyInProgressRuntimeException(OperationType.AUTHORISATION.getValue(), chargeId);
                default:
                    throw new GenericGatewayRuntimeException("Exception occurred while doing authorisation");
            }
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    protected abstract ChargeEntity preOperation(ChargeEntity chargeEntity, T gatewayAuthRequest);

    protected abstract GatewayResponse postOperation(ChargeEntity preOperationResponse, T gatewayAuthRequest, GatewayResponse<BaseAuthoriseResponse> operationResponse);

    protected abstract GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity preOperationResponse, T gatewayAuthRequest);

    protected abstract List<ChargeStatus> getLegalStates();

}
