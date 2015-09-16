package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviderFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.ChargeStatus.STATUS_KEY;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class ChargeCaptureResource {

    private final ChargeDao chargeDao;
    private final GatewayAccountDao accountDao;
    private final Logger logger = LoggerFactory.getLogger(ChargeCaptureResource.class);

    public ChargeCaptureResource(ChargeDao chargeDao, GatewayAccountDao accountDao) {
        this.chargeDao = chargeDao;
        this.accountDao = accountDao;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/capture")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response addCardDetailsForCharge(@PathParam("chargeId") String chargeId) {

        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        if (!maybeCharge.isPresent()) {
            return responseWithChargeNotFound(logger, chargeId);
        }

        Map<String, Object> charge = maybeCharge.get();
        if (!isAuthorised(charge)) {
            return responseWithChargeStatusIncorrect((String) charge.get(STATUS_KEY));
        }

        String amount = String.valueOf(charge.get("amount"));
        PaymentProvider paymentProvider = resolvePaymentProviderFor(charge);
        CaptureRequest request = new CaptureRequest(new Amount(amount), chargeId);
        CaptureResponse captureResponse = paymentProvider.capture(request);

        if(captureResponse.isSuccessful()){
            chargeDao.updateStatus(chargeId, CAPTURED);
            return Response.noContent().build();
        }

        //TODO
        return null;
    }

    private PaymentProvider resolvePaymentProviderFor(Map<String, Object> charge) {
        Optional<Map<String, Object>> maybeAccount = accountDao.findById((String) charge.get("gateway_account_id"));
        String paymentProviderName = (String) maybeAccount.get().get("payment_provider");
        return PaymentProviderFactory.resolve(paymentProviderName).orElseThrow(unsupportedProvider(paymentProviderName));
    }

    private Supplier<RuntimeException> unsupportedProvider(String paymentProviderName) {
        return () -> new RuntimeException("Unsupported PaymentProvider " + paymentProviderName);
    }

    private Response responseWithChargeStatusIncorrect(String status) {
        return badRequestResponse(logger, format("Cannot capture a charge with status %s.", status));
    }

    private static boolean isAuthorised(Map<String, Object> charge) {
        return AUTHORISATION_SUCCESS.getValue().equals(charge.get(STATUS_KEY));
    }
}
