package uk.gov.pay.connector.resources;

import io.dropwizard.auth.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.StatusUpdates;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.ServiceAccount;
import uk.gov.pay.connector.service.PaymentProviders;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

import static javax.ws.rs.core.Response.Status.BAD_GATEWAY;


//FIXME: redesign needed
@Path("/")
public class NotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(NotificationResource.class);
    private PaymentProviders providers;
    private ChargeDao chargeDao;
    private GatewayAccountDao accountDao;

    public NotificationResource(PaymentProviders providers, ChargeDao chargeDao, GatewayAccountDao accountDao) {
        this.providers = providers;
        this.chargeDao = chargeDao;
        this.accountDao = accountDao;
    }

    @POST
    @Path("v1/api/notifications/smartpay")
    public Response authoriseSmartpayNotifications(@Auth String username, String notification) throws IOException {
        logger.info("Received notification from smartpay" + notification);

        ServiceAccount dummyServiceAccount = new ServiceAccount(-111L, "smartpay", new HashMap<String, String>());

        StatusUpdates response = providers.resolve("smartpay").newStatusFromNotification(dummyServiceAccount, notification);

        if (!response.successful()) {
            return Response.status(BAD_GATEWAY).build();
        }

        response.getStatusUpdates().forEach(update -> updateCharge(chargeDao, update.getKey(), update.getValue()));

        return Response.ok(response.getResponseForProvider()).build();
    }

    @POST
    @Path("v1/api/notifications/{provider}")
    public Response handleNotification(@PathParam("provider") String provider, String notification) {
        logger.info("Received notification from " + provider + ": " + notification);

       Optional<String> transactionId =  providers.resolve(provider).getNotificationTransactionId(notification);



        Optional<String> serviceAccountId = chargeDao.findAccountByTransactionId(provider, transactionId.get());
        Optional<ServiceAccount> serviceAccount = accountDao.findById(serviceAccountId.get());

        StatusUpdates response = providers.resolve(provider).newStatusFromNotification(serviceAccount.get(), transactionId.get());

        if (!response.successful()) {
            return Response.status(BAD_GATEWAY).build();
        }

        response.getStatusUpdates().forEach(update -> updateCharge(chargeDao, update.getKey(), update.getValue()));

        return Response.ok(response.getResponseForProvider()).build();
    }

    private static void updateCharge(ChargeDao chargeDao, String key, ChargeStatus value) {
        try {
            chargeDao.updateStatusWithGatewayInfo(key, value);
        } catch (PayDBIException e) {
            logger.error("Error when trying to update transaction id " + key + " to status " + value, e);
        }
    }
}