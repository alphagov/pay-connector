package uk.gov.pay.connector.resources;

import uk.gov.pay.connector.dao.PaymentDao;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;

@Path("/frontend/payment")
public class PaymentInfo {
    private PaymentDao paymentDao;

    public PaymentInfo(PaymentDao paymentDao) {
        this.paymentDao = paymentDao;
    }

    @GET
    @Path("/{payId}")
    @Produces(APPLICATION_JSON)
    public Response getPayment(@PathParam("payId") long payId) {
        long amount = paymentDao.getAmountById(payId);

        String response = format("{\"amount\": %d}", amount);

        return ok(response).build();
    }
}
