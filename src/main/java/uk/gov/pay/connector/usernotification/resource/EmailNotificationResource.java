package uk.gov.pay.connector.usernotification.resource;

import com.google.common.collect.ImmutableSet;
import com.google.inject.persist.Transactional;
import io.dropwizard.jersey.PATCH;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.EmailNotificationPatchRequest;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class EmailNotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationResource.class);

    public static final String EMAIL_NOTIFICATION_TEMPLATE_BODY = "template_body";
    private static final String EMAIL_NOTIFICATION_ENABLED = "enabled";

    private static final String FORMATTER = "/%s/%s";
    private static final Set<String> VALID_PATHS = ImmutableSet.of(
            format(FORMATTER, EmailNotificationType.PAYMENT_CONFIRMED.toString().toLowerCase(), EMAIL_NOTIFICATION_TEMPLATE_BODY),
            format(FORMATTER, EmailNotificationType.PAYMENT_CONFIRMED.toString().toLowerCase(), EMAIL_NOTIFICATION_ENABLED),
            format(FORMATTER, EmailNotificationType.REFUND_ISSUED.toString().toLowerCase(), EMAIL_NOTIFICATION_TEMPLATE_BODY),
            format(FORMATTER, EmailNotificationType.REFUND_ISSUED.toString().toLowerCase(), EMAIL_NOTIFICATION_ENABLED)
    );
    private final GatewayAccountDao gatewayDao;

    @Inject
    public EmailNotificationResource(GatewayAccountDao gatewayDao) {
        this.gatewayDao = gatewayDao;
    }

    @PATCH
    @Path("/v1/api/accounts/{accountId}/email-notification")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Enables/disables email notifications for gateway account",
            description = "Allowed paths <br>" +
                    " - /payment_confirmed/enabled (values true/false) <br>" +
                    " - /refund_issued/enabled (values true/false) <br>" +
                    " - /payment_confirmed/template_body<br>" +
                    " - /refund_issued/template_body",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "    \"op\":\"replace\", \"path\":\"/payment_confirmed/enabled\", \"value\": false" +
                    "}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid or missing mandatory fields"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response enableEmailNotification(@Parameter(example = "1", description = "Gateway account ID")
                                            @PathParam("accountId") Long gatewayAccountId,
                                            @Valid EmailNotificationPatchRequest request) {

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount -> {
                    NotificationPatchInfo patchInfo = getNotificationInfoFromPath(request);
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

    private NotificationPatchInfo getNotificationInfoFromPath(EmailNotificationPatchRequest emailPatchRequest) {
        List<String> pathTokens = emailPatchRequest.getPathTokens();
        return new NotificationPatchInfo(EmailNotificationType.fromString(pathTokens.get(0)), pathTokens.get(1), emailPatchRequest.getValue());
    }

    private void patch(EmailNotificationEntity emailNotificationEntity, NotificationPatchInfo patchInfo) {
        switch (patchInfo.getPath()) {
            case EMAIL_NOTIFICATION_ENABLED:
                emailNotificationEntity.setEnabled(Boolean.parseBoolean(patchInfo.getValue()));
                break;
            case EMAIL_NOTIFICATION_TEMPLATE_BODY:
                emailNotificationEntity.setTemplateBody(patchInfo.getValue());
                break;
        }
    }

    private EmailNotificationEntity newDisabledEmailNotificationEntityWithNoTemplate(GatewayAccountEntity gatewayAccount, EmailNotificationType type) {
        EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(gatewayAccount, null, false);
        gatewayAccount.addNotification(type, emailNotificationEntity);
        return emailNotificationEntity;
    }

    private class NotificationPatchInfo {

        private final EmailNotificationType emailNotificationType;
        private final String path;
        private final String value;

        NotificationPatchInfo(EmailNotificationType emailNotificationType, String path, String value) {
            this.emailNotificationType = emailNotificationType;
            this.path = path;
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        EmailNotificationType getEmailNotificationType() {
            return emailNotificationType;
        }

        public String getPath() {
            return path;
        }
    }

}
