package uk.gov.pay.connector.resources;

import io.dropwizard.auth.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.StatusUpdates;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviders;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

import static javax.ws.rs.core.Response.Status.BAD_GATEWAY;

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
        return handleNotification("smartpay", notification);
    }

    @POST
    @Path("v1/api/notifications/{provider}")
    public Response handleNotification(@PathParam("provider") String provider, String notification) {

        logger.info("Received notification from " + provider + ": " + notification);

        PaymentProvider paymentProvider = providers.resolve(provider);
        StatusUpdates statusUpdates = paymentProvider.handleNotification(notification, findAccountByTransactionId(provider), accountUpdater(provider));

        if (!statusUpdates.successful()) {
            return Response.status(BAD_GATEWAY).build();
        }

        return Response.ok(statusUpdates.getResponseForProvider()).build();
    }

    private Consumer<StatusUpdates> accountUpdater(String provider) {
        return statusUpdates ->
                statusUpdates.getStatusUpdates().forEach(update -> updateCharge(chargeDao, provider , update.getKey(), update.getValue()));
    }


    private Function<String, GatewayAccount> findAccountByTransactionId(String provider) {
        return transactionId -> {
            String accountId = chargeDao.findAccountByTransactionId(provider, transactionId).get();
            return accountDao.findById(accountId).get();
        };
    }

    private static void updateCharge(ChargeDao chargeDao, String provider, String transactionId, ChargeStatus value) {
        try {
            chargeDao.updateStatusWithGatewayInfo(provider, transactionId, value);
        } catch (PayDBIException e) {
            logger.error("Error when trying to update transaction id " + transactionId + " to status " + value, e);
        }
    }
}