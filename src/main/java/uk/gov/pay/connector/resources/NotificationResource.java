package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.service.*;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;

@Path("/")
public class NotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(NotificationResource.class);

    private final NotificationService notificationService;

    @Inject
    public NotificationResource(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @PermitAll
    @Path("v1/api/notifications/smartpay")
    public Response authoriseSmartpayNotifications(String notification) throws IOException {
        return handleNotification("smartpay", notification);
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path("v1/api/notifications/sandbox")
    public Response authoriseSandboxNotifications(String notification) throws IOException {
        return handleNotification("sandbox", notification);
    }

    @POST
    @Consumes(TEXT_XML)
    @Path("v1/api/notifications/worldpay")
    public Response authoriseWorldpayNotifications(String notification) throws IOException {
        return handleNotification("worldpay", notification);
    }

    private Response handleNotification(String provider, String notification) {

        logger.info("Received notification from provider=" + provider + ", notification=" + notification);

        notificationService.acceptNotificationFor(provider, notification);

        //StatusUpdates statusUpdates = paymentProvider.handleNotification(notification, notificationUtil::payloadChecks, findAccountByTransactionId(provider), accountUpdater(provider));

        return Response.ok().build();
    }

   /* private Consumer<StatusUpdates> accountUpdater(String provider) {
        return statusUpdates ->
                statusUpdates.getStatusUpdates().forEach(update -> updateCharge(provider, update.getKey(), update.getValue()));
    }

    private Function<String, Optional<ChargeEntity>> findAccountByTransactionId(String provider) {
        return transactionId ->
                Optional.ofNullable(
                        chargeDao.findByProviderAndTransactionId(provider, transactionId)
                                .orElseGet(() -> {
                                    logger.error("Could not find account for transaction id " + transactionId);
                                    return null;
                                }));
    }*/

   /* private void updateCharge(String provider, String transactionId, ChargeStatus status) {
        Optional<ChargeEntity> charge = chargeDao.findByProviderAndTransactionId(provider, transactionId);
        if (charge.isPresent()) {
            chargeService.updateStatus(singletonList(charge.get()), status);
        } else {
            logger.error("Error when trying to update transaction id " + transactionId + " to status " + status);
        }
    }*/
}
