package uk.gov.pay.connector.telephone;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.telephone.service.StripeTelephonePaymentService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/v1/api/ivr")
public class TelephonePaymentsResource {
    
    private static final Logger logger = LoggerFactory.getLogger(TelephonePaymentsResource.class);
    private ChargeService chargeService;
    private StripeTelephonePaymentService stripeTelephonePaymentService;
    private ChargeDao chargeDao;
    private GatewayAccountDao gatewayAccountDao;
    
    @Inject
    TelephonePaymentsResource(ChargeService chargeService, StripeTelephonePaymentService stripeTelephonePaymentService,
                              ChargeDao chargeDao, GatewayAccountDao gatewayAccountDao) {
        this.chargeService = chargeService;
        this.stripeTelephonePaymentService = stripeTelephonePaymentService;
        this.chargeDao = chargeDao;
        this.gatewayAccountDao = gatewayAccountDao;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path("/create-payment")
    public Response createPayment(TelephonePaymentRequest telephonePaymentRequest) {
        logger.info("Received request to create a telephone payment for Stripe id " + telephonePaymentRequest.getStripeId());
        stripeTelephonePaymentService.getStripePayment(telephonePaymentRequest.getStripeId()).ifPresent(payment -> {
            logger.info(payment.toString());
            var telephoneRequestBuilder = new TelephoneChargeCreateRequest.Builder()
                    .withAmount(payment.getAmount())
                    .withReference(payment.getDescription())
                    .withDescription(payment.getDescription())
                    .withCreatedDate("2018-02-21T16:05:13Z")
                    .withAuthorisedDate("2018-02-21T16:05:33Z")
                    .withProcessorId("1PROC")
                    .withProviderId("1PROV")
                    .withAuthCode(payment.getAuthorizationCode())
                    .withNameOnCard(payment.getBillingDetails().getName())
                    .withEmailAddress(payment.getBillingDetails().getEmail())
                    .withTelephoneNumber(payment.getBillingDetails().getPhone())
                    .withCardType(payment.getPaymentMethodDetails().getCard().getBrand())
                    .withCardExpiry(stripeTelephonePaymentService.formatStripeExpiryDate(payment))
                    .withLastFourDigits(payment.getPaymentMethodDetails().getCard().getLast4())
                    .withPaymentOutcome(new PaymentOutcome("success"))
                    .withFirstSixDigits("123456");
            var response = chargeService.create(telephoneRequestBuilder.build(), telephonePaymentRequest.getAccountId());
            response.ifPresent(chargeResponse -> {
                logger.info("Charge Response " + chargeResponse.toString());
            });
        });
        return Response.ok().build();
    }
}
