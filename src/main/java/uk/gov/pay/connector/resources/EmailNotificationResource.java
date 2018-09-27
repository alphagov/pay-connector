package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableSet;
import com.google.inject.persist.Transactional;
import io.dropwizard.jersey.PATCH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.EmailNotificationsDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.builder.PatchRequestBuilder;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.model.domain.EmailNotificationType;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.builder.PatchRequestBuilder.aPatchRequestBuilder;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.fieldsMissingResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class EmailNotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationResource.class);

    // PP-4111 remove this after selfservice is merged
    public static final String EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD = "custom-email-text";
    public static final String EMAIL_NOTIFICATION_TEMPLATE_BODY = "template_body";
    public static final String EMAIL_NOTIFICATION_ENABLED = "enabled";

    public static final Set<String> VALID_PATHS = ImmutableSet.of(
            format("/%s/%s", EmailNotificationType.PAYMENT_CONFIRMED.toString().toLowerCase(), EMAIL_NOTIFICATION_TEMPLATE_BODY),
            format("/%s/%s", EmailNotificationType.PAYMENT_CONFIRMED.toString().toLowerCase(), EMAIL_NOTIFICATION_ENABLED),
            format("/%s/%s", EmailNotificationType.REFUND_ISSUED.toString().toLowerCase(), EMAIL_NOTIFICATION_TEMPLATE_BODY),
            format("/%s/%s", EmailNotificationType.REFUND_ISSUED.toString().toLowerCase(), EMAIL_NOTIFICATION_ENABLED),
            EMAIL_NOTIFICATION_ENABLED // PP-4111 remove this after selfservice is merged, backward compatible
    );
    private final EmailNotificationsDao emailNotificationsDao;
    private final GatewayAccountDao gatewayDao;

    @Inject
    public EmailNotificationResource(GatewayAccountDao gatewayDao, EmailNotificationsDao emailNotificationsDao) {
        this.emailNotificationsDao = emailNotificationsDao;
        this.gatewayDao = gatewayDao;
    }

    //PP-4111 backward compatibility, remove after selfservice is merged
    @GET
    @Path("/v1/api/accounts/{accountId}/email-notification")
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response getEmailNotificationText(@PathParam("accountId") Long gatewayAccountId) {
        logger.info("Getting email notification text for account id {}", gatewayAccountId);

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount ->
                        emailNotificationsDao.findByAccountId(gatewayAccount.getId())
                                .map(emailNotificationEntity -> Response.ok().entity(emailNotificationEntity).build())
                                .orElseGet(() -> Response.ok().build()))
                .orElseGet(() -> notFoundResponse(format("Account with id %s not found.", gatewayAccountId)));
    }


    // PP-4111 This is just used to update the template body. Will be removed after selfservice stops using it
    @POST
    @Path("/v1/api/accounts/{accountId}/email-notification")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response updateEmailNotification(@PathParam("accountId") Long gatewayAccountId, Map<String, String> payload) {
        if (!payload.containsKey(EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD)) {
            return fieldsMissingResponse(Collections.singletonList(EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD));
        }

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount ->
                        emailNotificationsDao.findByAccountId(gatewayAccountId).map(emailNotificationEntity -> {
                            emailNotificationEntity.setTemplateBody(payload.get(EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD));
                            return Response.ok().build();
                        }).orElseGet(() -> {
                            gatewayAccount.addNotification(EmailNotificationType.PAYMENT_CONFIRMED,
                                    new EmailNotificationEntity(gatewayAccount, payload.get(EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD)));
                            return Response.ok().build();
                        }))
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }


    @PATCH
    @Path("/v1/api/accounts/{accountId}/email-notification")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response enableEmailNotification(@PathParam("accountId") Long gatewayAccountId, Map<String, String> emailPatchMap) {
        PatchRequestBuilder.PatchRequest emailPatchRequest;
        try {
            emailPatchRequest = aPatchRequestBuilder(emailPatchMap)
                    .withValidOps(Collections.singletonList("replace"))
                    .withValidPaths(VALID_PATHS)
                    .build();
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse("Bad patch parameters" + emailPatchMap.toString());
        }

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount -> {
                    NotificationPatchInfo patchInfo = getNotificationInfoFromPath(emailPatchRequest);
                    EmailNotificationType type = patchInfo.getEmailNotificationType();
                    EmailNotificationEntity notificationEntity = Optional.ofNullable(gatewayAccount.getEmailNotifications().get(type))
                            .orElseGet(() -> {
                                //PP-4111 we are not going to backfill and add refund notifications for existing gateway accounts, so this is unfortunately needed
                                return newDisabledEmailNotificationEntityWithNoTemplate(gatewayAccount, type);
                            });
                    patch(notificationEntity, patchInfo);
                    return Response.ok().build();
                })
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    private NotificationPatchInfo getNotificationInfoFromPath(PatchRequestBuilder.PatchRequest emailPatchRequest) {
        List<String> pathTokens = emailPatchRequest.getPathTokens();
        // PP-4111 remove after selfservice is merged
        if (pathTokens.size() < 2) {
            return new NotificationPatchInfo(EmailNotificationType.PAYMENT_CONFIRMED, "enabled", emailPatchRequest.getValue());
        }
        return new NotificationPatchInfo(EmailNotificationType.fromString(pathTokens.get(0)), pathTokens.get(1), emailPatchRequest.getValue());
    }
    
    private void patch(EmailNotificationEntity emailNotificationEntity, NotificationPatchInfo patchInfo) {
        switch (patchInfo.getPath()) {
            case EMAIL_NOTIFICATION_ENABLED:
                emailNotificationEntity.setEnabled(Boolean.parseBoolean(patchInfo.getValue()));
                break;
            case EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD:
            case EMAIL_NOTIFICATION_TEMPLATE_BODY:
                emailNotificationEntity.setTemplateBody(patchInfo.getValue());
                break;
        }
    }
    
    private EmailNotificationEntity newDisabledEmailNotificationEntityWithNoTemplate(GatewayAccountEntity gatewayAccount, EmailNotificationType type) {
        EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(gatewayAccount, null, false);
        gatewayAccount.addNotification(type, emailNotificationEntity);
        return  emailNotificationEntity;
    }

    private class NotificationPatchInfo {

        private final EmailNotificationType emailNotificationType;
        private final String path;
        private final String value;

        public NotificationPatchInfo(EmailNotificationType emailNotificationType, String path, String value) {
            this.emailNotificationType = emailNotificationType;
            this.path = path;
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public EmailNotificationType getEmailNotificationType() {
            return emailNotificationType;
        }

        public String getPath() {
            return path;
        }
    }

}
