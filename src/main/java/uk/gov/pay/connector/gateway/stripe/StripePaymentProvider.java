package uk.gov.pay.connector.gateway.stripe;

import com.stripe.exception.StripeException;
import io.dropwizard.core.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.DeleteStoredPaymentDetailsGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeAuthoriseHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCancelHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeDisputeHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeFailedPaymentFeeCollectionHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeQueryPaymentStatusHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeRefundHandler;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;
import uk.gov.pay.connector.gateway.stripe.json.StripeTransfer;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferInRequest;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsRequiredParams;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY;

@Singleton
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final GatewayClient client;
    private final JsonObjectMapper jsonObjectMapper;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final StripeCaptureHandler stripeCaptureHandler;
    private final StripeCancelHandler stripeCancelHandler;
    private final StripeRefundHandler stripeRefundHandler;
    private final StripeAuthoriseHandler stripeAuthoriseHandler;
    private final StripeFailedPaymentFeeCollectionHandler stripeFailedPaymentFeeCollectionHandler;
    private final StripeQueryPaymentStatusHandler stripeQueryPaymentStatusHandler;
    private final StripeDisputeHandler stripeDisputeHandler;
    private final StripeSdkClient stripeSDKClient;

    @Inject
    public StripePaymentProvider(GatewayClientFactory gatewayClientFactory,
                                 ConnectorConfiguration configuration,
                                 JsonObjectMapper jsonObjectMapper,
                                 Environment environment,
                                 StripeSdkClient stripeSDKClient) {
        this.stripeGatewayConfig = configuration.getStripeConfig();
        this.stripeSDKClient = stripeSDKClient;
        this.client = gatewayClientFactory.createGatewayClient(STRIPE, environment.metrics());
        this.jsonObjectMapper = jsonObjectMapper;
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        stripeCaptureHandler = new StripeCaptureHandler(client, stripeGatewayConfig, jsonObjectMapper);
        stripeCancelHandler = new StripeCancelHandler(client, stripeGatewayConfig);
        stripeRefundHandler = new StripeRefundHandler(client, stripeGatewayConfig, jsonObjectMapper);
        stripeAuthoriseHandler = new StripeAuthoriseHandler(client, stripeGatewayConfig, configuration, jsonObjectMapper);
        stripeFailedPaymentFeeCollectionHandler = new StripeFailedPaymentFeeCollectionHandler(client, stripeGatewayConfig, jsonObjectMapper);
        stripeQueryPaymentStatusHandler = new StripeQueryPaymentStatusHandler(client, stripeGatewayConfig, jsonObjectMapper);
        stripeDisputeHandler = new StripeDisputeHandler(client, stripeGatewayConfig, jsonObjectMapper);
    }

    @Override
    public boolean canQueryPaymentStatus() {
        return true;
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return STRIPE;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeQueryGatewayRequest chargeQueryGatewayRequest) throws GatewayException {
        return stripeQueryPaymentStatusHandler.queryPaymentStatus(chargeQueryGatewayRequest);
    }

    @Override
    public GatewayResponse authorise(CardAuthorisationGatewayRequest request, ChargeEntity charge) {
        return stripeAuthoriseHandler.authorise(request);
    }

    @Override
    public GatewayResponse authoriseUserNotPresent(RecurringPaymentAuthorisationGatewayRequest request) {
        return stripeAuthoriseHandler.authoriseUserNotPresent(request);
    }

    /**
     * IMPORTANT: this method should not attempt to update the Charge in the database. This is because it is executed
     * on a worker thread and the initiating thread can attempt to update the Charge status while it is still being
     * executed.
     */
    @Override
    public GatewayResponse authoriseMotoApi(CardAuthorisationGatewayRequest request) {
        return stripeAuthoriseHandler.authorise(request);
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {

        if (request.getAuth3dsResult() != null && request.getAuth3dsResult().getAuth3dsResultOutcome() != null) {
            Gateway3dsRequiredParams params = new Stripe3dsRequiredParams(request.getCharge().get3dsRequiredDetails(), request.getAuth3dsResult().getThreeDsVersion());
            switch (request.getAuth3dsResult().getAuth3dsResultOutcome()) {
                case CANCELED:
                    return Gateway3DSAuthorisationResponse.of(request.getAuth3dsResult().getGatewayResponseStringified(), BaseAuthoriseResponse.AuthoriseStatus.CANCELLED, params);
                case ERROR:
                    return Gateway3DSAuthorisationResponse.of(request.getAuth3dsResult().getGatewayResponseStringified(), BaseAuthoriseResponse.AuthoriseStatus.ERROR, params);
                case DECLINED:
                    return Gateway3DSAuthorisationResponse.of(request.getAuth3dsResult().getGatewayResponseStringified(), BaseAuthoriseResponse.AuthoriseStatus.REJECTED, params);
                case AUTHORISED:
                    return Gateway3DSAuthorisationResponse.of(request.getAuth3dsResult().getGatewayResponseStringified(), BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED, params);
            }
        }

        // if Auth3DSResult is not available, return response as AUTH_3DS_READY
        // (to keep the transaction in auth waiting until a notification is received from Stripe for 3DS authorisation)
        return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.AUTH_3DS_READY);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseApplePay(ApplePayAuthorisationGatewayRequest authorisationGatewayRequest) throws GatewayException {
        return stripeAuthoriseHandler.authoriseApplePay(authorisationGatewayRequest);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseGooglePay(GooglePayAuthorisationGatewayRequest authorisationGatewayRequest) throws GatewayException {
        return stripeAuthoriseHandler.authoriseGooglePay(authorisationGatewayRequest);
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return stripeCaptureHandler.capture(request);
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        return stripeRefundHandler.refund(request);
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        return stripeCancelHandler.cancel(request);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(Charge charge, List<Refund> refundList) {
        return externalRefundAvailabilityCalculator.calculate(charge, refundList);
    }

    @Override
    public StripeAuthorisationRequestSummary generateAuthorisationRequestSummary(GatewayAccountEntity gatewayAccount, AuthCardDetails authCardDetails, boolean isSetUpAgreement) {
        return new StripeAuthorisationRequestSummary(authCardDetails, isSetUpAgreement);
    }

    @Override
    public void deleteStoredPaymentDetails(DeleteStoredPaymentDetailsGatewayRequest request) throws GatewayException {
        var customerId = request.getRecurringAuthToken().get(STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY);
        try {
            stripeSDKClient.deleteCustomer(customerId, request.isLive());
        } catch (StripeException e) {
            var message = format("Error when attempting to delete Stripe customer %s. Status code: %s, Error code: %s, Message: %s",
                    customerId, e.getStatusCode(), e.getCode(), e.getMessage());
            throw new GatewayException.GenericGatewayException(message);
        }
    }

    public List<Fee> calculateAndTransferFeesForFailedPayments(ChargeEntity charge) throws GatewayException {
        return stripeFailedPaymentFeeCollectionHandler.calculateAndTransferFees(charge);
    }

    public StripeDisputeData submitTestDisputeEvidence(String disputeId, String evidenceText, String transactionId) throws GatewayException {
        return stripeDisputeHandler.submitTestDisputeEvidence(disputeId, evidenceText, transactionId);
    }

    public void transferDisputeAmount(StripeDisputeData stripeDisputeData, Charge charge, GatewayAccountEntity gatewayAccount,
                                      GatewayAccountCredentialsEntity gatewayAccountCredentials, long transferAmount) throws GatewayException {
        
        String disputeExternalId = RandomIdGenerator.idFromExternalId(stripeDisputeData.getId());

        StripeTransferInRequest transferInRequest = StripeTransferInRequest.createDisputeTransferRequest(
                String.valueOf(transferAmount),
                gatewayAccount,
                gatewayAccountCredentials,
                stripeDisputeData.getPaymentIntentId(),
                disputeExternalId,
                charge.getExternalId(),
                stripeGatewayConfig);

        String rawResponse = client.postRequestFor(transferInRequest).getEntity();
        StripeTransfer stripeTransfer = jsonObjectMapper.getObject(rawResponse, StripeTransfer.class);

        logger.info("Funds transferred for dispute {} for charge {}, transferred net amount {} - transfer id {} - from Stripe Connect account id {} in transfer group {}",
                disputeExternalId,
                charge.getExternalId(),
                transferAmount,
                stripeTransfer.getId(),
                stripeTransfer.getDestinationStripeAccountId(),
                stripeTransfer.getStripeTransferGroup()
        );
    }
}
