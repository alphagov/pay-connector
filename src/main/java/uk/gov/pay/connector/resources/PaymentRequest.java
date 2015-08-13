package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.pay.connector.dao.PaymentDao;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/payment")
public class PaymentRequest {
    private PaymentDao paymentDao;

    public PaymentRequest(PaymentDao paymentDao) {
        this.paymentDao = paymentDao;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewPayment(JsonNode node) {
        long amount = node.get("amount").asLong();

        long payId = paymentDao.insertAmountAndReturnNewId(amount);

        String response = format("{\"pay_id\":\"%s\"}", payId);

        return Response.ok(response).build();
    }
}
