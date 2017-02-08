package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.Auth3dsDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.inject.Inject;

import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class Card3dsResponseAuthService extends CardAuthoriseBaseService<Auth3dsDetails> {

    @Inject
    public Card3dsResponseAuthService(ChargeDao chargeDao,
                                      PaymentProviders providers,
                                      CardExecutorService cardExecutorService,
                                      MetricRegistry metricRegistry) {
        super(chargeDao, providers, cardExecutorService, metricRegistry);
    }


    public GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity chargeEntity, Auth3dsDetails auth3DsDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise3dsResponse(Auth3dsResponseGatewayRequest.valueOf(chargeEntity, auth3DsDetails));
    }

    @Transactional
    public GatewayResponse<BaseAuthoriseResponse> postOperation(ChargeEntity chargeEntity,
                                                                Auth3dsDetails auth3DsDetails,
                                                                GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        // work out what to do
        return operationResponse;
    }

    @Override
    protected ChargeStatus[] getLegalStates() {
        return new ChargeStatus[]{
                ENTERING_CARD_DETAILS
        };
    }

}