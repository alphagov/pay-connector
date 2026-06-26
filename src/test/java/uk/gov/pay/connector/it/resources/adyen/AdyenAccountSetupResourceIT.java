package uk.gov.pay.connector.it.resources.adyen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;
import uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers;

import java.util.Arrays;

import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus.COMPLETED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.CreateGatewayAccountPayloadBuilder.aCreateGatewayAccountPayloadBuilder;

public class AdyenAccountSetupResourceIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    public static GatewayAccountResourceITHelpers testHelpers = new GatewayAccountResourceITHelpers(app.getLocalPort());
    private String serviceId;
    private String accountType;
    private Long credentialId;

    @BeforeEach
    void setUp() {
        serviceId = "service-123";
        accountType = String.valueOf(TEST);
        credentialId = 1L;
    }

    @Test
    void withAllTasksCompletedReturnsTrueFlags() {
        testHelpers.createGatewayAccount(
                aCreateGatewayAccountPayloadBuilder()
                        .withServiceId(serviceId)
                        .withProvider(ADYEN.getName())
                        .build());
        
        Arrays.stream(AdyenAccountSetupTask.values()).forEach(task -> addCompletedTask(1L, 1L, task, COMPLETED));
        
        app.givenSetup()
                .get( format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, accountType, credentialId))
                .then()
                .statusCode(SC_OK)
                .body("bank_account", is(true))
                .body("responsible_person", is(true))
                .body("vat_number", is(true))
                .body("company_number", is(true))
                .body("director", is(true))
                .body("government_entity_document", is(true))
                .body("organisation_details", is(true));
    }

    @Test
    void returnsNotFoundResponseWhenGatewayAccountDoesNotExist() {
        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, accountType, credentialId))
                .then()
                .statusCode(SC_NOT_FOUND);
    }

    private void addCompletedTask(long gatewayAccountId, long credentialId, AdyenAccountSetupTask task, AdyenAccountSetupStatus status) {
        app.getDatabaseTestHelper().addGatewayAccountsAdyenSetupTask(gatewayAccountId, credentialId, task, status);
    }
}
