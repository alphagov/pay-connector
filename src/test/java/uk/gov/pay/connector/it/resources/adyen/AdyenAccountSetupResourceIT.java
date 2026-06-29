package uk.gov.pay.connector.it.resources.adyen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;
import uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers;

import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus.NOT_STARTED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.CreateGatewayAccountPayloadBuilder.aCreateGatewayAccountPayloadBuilder;

public class AdyenAccountSetupResourceIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    public static GatewayAccountResourceITHelpers testHelpers = new GatewayAccountResourceITHelpers(app.getLocalPort());
    private String serviceId;
    private String accountType;
    private String credentialExternalId;

    @BeforeEach
    void setUp() {
        serviceId = "service-123";
        accountType = String.valueOf(TEST);
        credentialExternalId = "credential-123";
    }

    @Test
    void returnsNotStartedForAllWithNoTasksCompleted() {
        long gatewayAccountId = Long.parseLong(testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withServiceId(serviceId)
                        .withProvider(ADYEN.getName())
                        .build()));
        
        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, accountType, credentialExternalId))
                .then()
                .statusCode(SC_OK)
                .body("service_id", is(serviceId))
                .body("credential_external_id", is(credentialExternalId))
                .body("gateway_account_id", is((int) gatewayAccountId))
                .body("tasks.bank_account.status", is(NOT_STARTED.toString()))
                .body("tasks.responsible_person.status", is(NOT_STARTED.toString()))
                .body("tasks.vat_number.status", is(NOT_STARTED.toString()))
                .body("tasks.company_number.status", is(NOT_STARTED.toString()))
                .body("tasks.director.status", is(NOT_STARTED.toString()))
                .body("tasks.government_entity_document.status", is(NOT_STARTED.toString()))
                .body("tasks.organisation_details.status", is(NOT_STARTED.toString()));
    }

    @Test
    void returnsNotFoundResponseWhenGatewayAccountDoesNotExist() {
        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, accountType, credentialExternalId))
                .then()
                .statusCode(SC_NOT_FOUND);
    }

    private void addTaskWithStatus(long gatewayAccountId, long credentialId, AdyenAccountSetupTask task, AdyenAccountSetupStatus status) {
        app.getDatabaseTestHelper().addGatewayAccountsAdyenSetupTask(gatewayAccountId, credentialId, task, status);
    }
}
