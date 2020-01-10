package uk.gov.pay.connector.telephone;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.service.ChargeService;
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
    
    @Inject
    TelephonePaymentsResource(ChargeService chargeService, StripeTelephonePaymentService stripeTelephonePaymentService, ChargeDao chargeDao) {
        this.chargeService = chargeService;
        this.stripeTelephonePaymentService = stripeTelephonePaymentService;
        this.chargeDao = chargeDao;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path("/create-payment")
    public Response createPayment(TelephonePaymentRequest telephonePaymentRequest) {
        logger.info("Received request to create a telephone payment for Stripe id " + telephonePaymentRequest.getStripeId());
        var telephoneRequestBuilder = new TelephoneChargeCreateRequest.Builder()
                .withAmount(100L)
                .withReference("Some reference")
                .withDescription("Some description")
                .withCreatedDate("2018-02-21T16:04:25Z")
                .withAuthorisedDate("2018-02-21T16:05:33Z")
                .withProcessorId("1PROC")
                .withProviderId("1PROV")
                .withAuthCode("666")
                .withNameOnCard("Jane Doe")
                .withEmailAddress("jane.doe@example.com")
                .withTelephoneNumber("+447700900796")
                .withCardType("visa")
                .withCardExpiry("01/19")
                .withLastFourDigits("1234")
                .withPaymentOutcome(new PaymentOutcome("success"))
                .withFirstSixDigits("123456");
        chargeService.create(telephoneRequestBuilder.build(), telephonePaymentRequest.getAccountId());
        stripeTelephonePaymentService.getStripePayment(telephonePaymentRequest.getStripeId()).ifPresent(payment -> {
            logger.info(payment.toString());
                chargeDao.persist(new ChargeEntity(payment.getAmount(),
                        null, 
                        payment.getDescription(), 
                        ChargeStatus.fromString(payment.getStatus()),
                        payment.getBillingDetails().getEmail(), 
                        new CardDetailsEntity(LastDigitsCardNumber.of(payment.getPaymentMethodDetails().getCard().getLast4()), 
                                null,
                                payment.getBillingDetails().getName(),
                                stripeTelephonePaymentService.formatStripeExpiryDate(payment), 
                                payment.getPaymentMethodDetails().getCard().getBrand(),
                                CardType.valueOf(payment.getPaymentMethodDetails().getType())),
                        null,
                        null,
                        payment.getId(),
                        null
            ));
        });
        return Response.ok().build();
    }
}
