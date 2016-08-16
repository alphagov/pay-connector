package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.StatusUpdates;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.*;
import uk.gov.pay.connector.util.NotificationUtil;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

@Path("/")
public class NotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(NotificationResource.class);
    private PaymentProviders providers;
    private ChargeService chargeService;
    private ChargeDao chargeDao;
    private NotificationUtil notificationUtil = new NotificationUtil(new ChargeStatusBlacklist());

    @Inject
    public NotificationResource(PaymentProviders providers, ChargeDao chargeDao, ChargeService chargeService) {
        this.providers = providers;
        this.chargeService = chargeService;
        this.chargeDao = chargeDao;
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

        logger.info("Received notification from " + provider + ": " + notification);

        PaymentProvider<BaseResponse> paymentProvider = providers.resolve(provider);
        StatusUpdates statusUpdates = paymentProvider.handleNotification(notification, notificationUtil::payloadChecks, findAccountByTransactionId(provider), accountUpdater(provider));

        if (!statusUpdates.successful()) {
            return Response.status(INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok(statusUpdates.getResponseForProvider()).build();
    }

    private Consumer<StatusUpdates> accountUpdater(String provider) {
        return statusUpdates ->
                statusUpdates.getStatusUpdates().forEach(update -> updateCharge(provider, update.getKey(), update.getValue()));
    }

    private Function<String, Optional<GatewayAccountEntity>> findAccountByTransactionId(String provider) {
        return transactionId ->
                chargeDao.findByProviderAndTransactionId(provider, transactionId)
                        .map((chargeEntity) ->
                                Optional.of(chargeEntity.getGatewayAccount()))
                        .orElseGet(() -> {
                            logger.error("Could not find account for transaction id " + transactionId);
                            return Optional.empty();
                        });
    }

    private void updateCharge(String provider, String transactionId, ChargeStatus status) {
        Optional<ChargeEntity> charge = chargeDao.findByProviderAndTransactionId(provider, transactionId);
        if (charge.isPresent()) {
            chargeService.updateStatus(singletonList(charge.get()), status);
        } else {
            logger.error("Error when trying to update transaction id " + transactionId + " to status " + status);
        }
    }
}
