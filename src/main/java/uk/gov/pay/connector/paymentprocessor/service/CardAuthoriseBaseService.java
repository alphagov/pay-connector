package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.AuthorisationProvider;
import uk.gov.pay.connector.gateway.AuthorisationProviders;
import uk.gov.pay.connector.gateway.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.gateway.model.AuthorisationDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import java.util.Optional;
import java.util.function.Supplier;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus;

public abstract class CardAuthoriseBaseService<T extends AuthorisationDetails> {

    private final CardExecutorService cardExecutorService;

    protected final ChargeService chargeService;
    protected final ChargeDao chargeDao;
    protected final ChargeEventDao chargeEventDao;
    private final AuthorisationProviders providers;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected MetricRegistry metricRegistry;

    CardAuthoriseBaseService(ChargeDao chargeDao, ChargeEventDao chargeEventDao,
                                    AuthorisationProviders providers,
                                    CardExecutorService cardExecutorService,
                                    ChargeService chargeService,
                                    Environment environment) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.providers = providers;
        this.cardExecutorService = cardExecutorService;
        this.chargeService = chargeService;
        this.metricRegistry = environment.metrics();
    }

    public GatewayResponse<BaseAuthoriseResponse> doAuthorise(String chargeId, T gatewayAuthRequest) {
        Supplier authorisationSupplier = () -> {
            ChargeEntity charge = prepareChargeForAuthorisation(chargeId, gatewayAuthRequest);
            GatewayResponse<BaseAuthoriseResponse> operationResponse = authorise(charge, gatewayAuthRequest);
            processGatewayAuthorisationResponse(chargeId, gatewayAuthRequest, operationResponse);
            return operationResponse;
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
    }

    protected abstract ChargeEntity prepareChargeForAuthorisation(String chargeId, T gatewayAuthRequest);

    protected abstract void processGatewayAuthorisationResponse(String chargeId, T gatewayAuthRequest, GatewayResponse<BaseAuthoriseResponse> operationResponse);

    protected abstract GatewayResponse<BaseAuthoriseResponse> authorise(ChargeEntity charge, T gatewayAuthRequest);

    ChargeStatus determineChargeStatus(Optional<BaseAuthoriseResponse> baseResponse,
                                                 Optional<GatewayError> gatewayError) {

        return baseResponse
                .map(BaseAuthoriseResponse::authoriseStatus)
                .map(AuthoriseStatus::getMappedChargeStatus)
                .orElseGet(() -> gatewayError
                        .map(this::mapError)
                        .orElse(ChargeStatus.AUTHORISATION_ERROR));
    }

    AuthorisationProvider<BaseAuthoriseResponse> getAuthorisationProviderFor(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName());
    }

    private ChargeStatus mapError(GatewayError gatewayError) {
        switch (gatewayError.getErrorType()) {
            case GENERIC_GATEWAY_ERROR:
                return AUTHORISATION_ERROR;
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
                return AUTHORISATION_TIMEOUT;
            default:
                return AUTHORISATION_UNEXPECTED_ERROR;
        }
    }
}
