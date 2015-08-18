package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.PaymentDao;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/v1/api/payment")
public class PaymentRequest {
    private PaymentDao paymentDao;
    private Logger logger = LoggerFactory.getLogger(PaymentRequest.class);

    public PaymentRequest(PaymentDao paymentDao) {
        this.paymentDao = paymentDao;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewPayment(JsonNode node, @Context UriInfo uriInfo) {
        long amount = node.get("amount").asLong();

        long payId = paymentDao.insertAmountAndReturnNewId(amount);

        String response = format("{\"pay_id\":\"%s\"}", payId);

        logger.info("Test.");

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path(PaymentInfo.getPaymentRoute).build(payId);

        return Response.created(newLocation).entity(response).build();


    }
}
