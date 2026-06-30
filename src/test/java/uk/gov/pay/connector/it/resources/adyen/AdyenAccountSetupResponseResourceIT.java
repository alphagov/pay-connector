package uk.gov.pay.connector.it.resources.adyen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;

import java.util.Arrays;
import java.util.List;

import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus.COMPLETED;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus.NOT_STARTED;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.BANK_ACCOUNT;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.COMPANY_NUMBER;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.DIRECTOR;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.GOVERNMENT_ENTITY_DOCUMENT;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.ORGANISATION_DETAILS;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.RESPONSIBLE_PERSON;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.VAT_NUMBER;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

public class AdyenAccountSetupResponseResourceIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private String serviceId;
    private String credentialExternalId;

    @BeforeEach
    void setUp() {
        serviceId = "service-123";
        credentialExternalId = "credential-123";
    }

    @Test
    void returnsNotStartedForAllWithNoTasksCompletedForALiveAccount() {
        var liveAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId(serviceId)
                .withPaymentProvider(ADYEN.getName())
                .withType(LIVE)
                .insert();

        long gatewayAccountId = liveAccount.getAccountId();
        
        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, LIVE, credentialExternalId))
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
    void returnsCompletedForAllWithAllTasksCompletedForALiveAccount() {
        var liveAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId(serviceId)
                .withPaymentProvider(ADYEN.getName())
                .withType(LIVE)
                .insert();

        long gatewayAccountId = liveAccount.getAccountId();
        
        var gatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialByPaymentProvider(gatewayAccountId, ADYEN.getName());
        markTasksAsCompleted(gatewayAccountId, gatewayAccountCredentialId, Arrays.asList(AdyenAccountSetupTask.values()));
        
        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, LIVE, credentialExternalId))
                .then()
                .statusCode(SC_OK)
                .body("service_id", is(serviceId))
                .body("credential_external_id", is(credentialExternalId))
                .body("gateway_account_id", is((int) gatewayAccountId))
                .body("tasks.bank_account.status", is(COMPLETED.toString()))
                .body("tasks.responsible_person.status", is(COMPLETED.toString()))
                .body("tasks.vat_number.status", is(COMPLETED.toString()))
                .body("tasks.company_number.status", is(COMPLETED.toString()))
                .body("tasks.director.status", is(COMPLETED.toString()))
                .body("tasks.government_entity_document.status", is(COMPLETED.toString()))
                .body("tasks.organisation_details.status", is(COMPLETED.toString()));
    }

    @Test
    void withSomeTasksCompletedForALiveAccount() {
        var liveAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId(serviceId)
                .withPaymentProvider(ADYEN.getName())
                .withType(LIVE)
                .insert();

        long gatewayAccountId = liveAccount.getAccountId();
        var gatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialByPaymentProvider(gatewayAccountId, ADYEN.getName());
        
        var completedTasks = List.of(BANK_ACCOUNT, RESPONSIBLE_PERSON, VAT_NUMBER);
        markTasksAsCompleted(gatewayAccountId, gatewayAccountCredentialId, completedTasks);
        
        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, LIVE, credentialExternalId))
                .then()
                .statusCode(SC_OK)
                .body("service_id", is(serviceId))
                .body("credential_external_id", is(credentialExternalId))
                .body("gateway_account_id", is((int) gatewayAccountId))
                .body("tasks.bank_account.status", is(COMPLETED.toString()))
                .body("tasks.responsible_person.status", is(COMPLETED.toString()))
                .body("tasks.vat_number.status", is(COMPLETED.toString()))
                .body("tasks.company_number.status", is(NOT_STARTED.toString()))
                .body("tasks.director.status", is(NOT_STARTED.toString()))
                .body("tasks.government_entity_document.status", is(NOT_STARTED.toString()))
                .body("tasks.organisation_details.status", is(NOT_STARTED.toString()));
    }

    @Test
    void withSomeTasksCompletedForATestAccount() {
        var testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId(serviceId)
                .withPaymentProvider(ADYEN.getName())
                .insert();

        long gatewayAccountId = testAccount.getAccountId();
        var gatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialByPaymentProvider(gatewayAccountId, ADYEN.getName());

        var completedTasks = List.of(COMPANY_NUMBER, DIRECTOR, GOVERNMENT_ENTITY_DOCUMENT, ORGANISATION_DETAILS);
        markTasksAsCompleted(gatewayAccountId, gatewayAccountCredentialId, completedTasks);
        
        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, TEST, credentialExternalId))
                .then()
                .statusCode(SC_OK)
                .body("service_id", is(serviceId))
                .body("credential_external_id", is(credentialExternalId))
                .body("gateway_account_id", is((int) gatewayAccountId))
                .body("tasks.bank_account.status", is(NOT_STARTED.toString()))
                .body("tasks.responsible_person.status", is(NOT_STARTED.toString()))
                .body("tasks.vat_number.status", is(NOT_STARTED.toString()))
                .body("tasks.company_number.status", is(COMPLETED.toString()))
                .body("tasks.director.status", is(COMPLETED.toString()))
                .body("tasks.government_entity_document.status", is(COMPLETED.toString()))
                .body("tasks.organisation_details.status", is(COMPLETED.toString()));
    }

    @Test
    void returnsNotFoundResponseWhenGatewayAccountDoesNotExist() {
        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, TEST, credentialExternalId))
                .then()
                .statusCode(SC_NOT_FOUND);
    }

    private void markTasksAsCompleted(long gatewayAccountId, long credentialId, List<AdyenAccountSetupTask> tasks) {
        tasks.forEach(task -> app.getDatabaseTestHelper().addGatewayAccountsAdyenSetupTask(gatewayAccountId, credentialId, task, COMPLETED));
    }
}
