package uk.gov.pay.connector.telephone;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.service.ChargeService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/v1/api/ivr")
public class TelephonePaymentsResource {
    
    private static final Logger logger = LoggerFactory.getLogger(TelephonePaymentsResource.class);
    private ChargeService chargeService;
    
    @Inject
    TelephonePaymentsResource(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path("/create-payment")
    public Response createPayment(TelephonePaymentRequest telephonePaymentRequest) {
        logger.info("Received request to create a telephone payment for Stripe id " + telephonePaymentRequest.getStripeId());
        var builder = new TelephoneChargeCreateRequest.Builder();
        var request = builder.withAmount(1337L)
                .withProviderId(telephonePaymentRequest.getStripeId())
                .withReference("test telephone payment")
                .withProcessorId("processor_id")
                .withPaymentOutcome(new PaymentOutcome("payment_outcome"))
                .build();
        chargeService.create(request, telephonePaymentRequest.getAccountId());
        return Response.ok().build();
    }
}
