package uk.gov.pay.connector.it.resources.adyen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus.COMPLETED;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus.NOT_STARTED;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.BANK_ACCOUNT;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.RESPONSIBLE_PERSON;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.VAT_NUMBER;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;

public class AdyenAccountSetupResourceIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private String serviceId;

    @BeforeEach
    void setUp() {
        serviceId = "service-123";
    }

    @Test
    void getTasksShouldReturnSomeTasksCompletedForALiveAccount() {
        LocalDateTime activeStartDate = LocalDate.parse("2021-02-01").atStartOfDay();
        
        AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                .withGatewayAccountId(500L)
                .withPaymentProvider(ADYEN.getName())
                .withActiveStartDate(activeStartDate.toInstant(ZoneOffset.UTC))
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();
        
        var liveAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withAccountId(500L)
                .withServiceId(serviceId)
                .withPaymentProvider(ADYEN.getName())
                .withType(LIVE)
                .withGatewayAccountCredentials(Collections.singletonList(credentialsParams))
                .insert();

        long gatewayAccountId = liveAccount.getAccountId();
        var gatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialByPaymentProvider(gatewayAccountId, ADYEN.getName());
        var credentialExternalId = liveAccount.getCredentials().getFirst().getExternalId();
        
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
    void getTasksShouldReturnNotFoundResponseWhenGatewayAccountDoesNotExist() {
        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, TEST, "credential-123"))
                .then()
                .statusCode(SC_NOT_FOUND);
    }

    @Test
    void getTasksShouldReturnNotFoundResponseWhenCredentialIdDoesNotExist() {
        app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId(serviceId)
                .withPaymentProvider(ADYEN.getName())
                .insert();
        
        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, TEST, "credential_does_not_exist"))
                .then()
                .statusCode(SC_NOT_FOUND);
    }

    @Test
    void getTasksShouldReturnNotFoundResponseWhenCredentialIdExistsButPaymentProviderIsNotAdyen() {
        var liveAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId(serviceId)
                .withType(LIVE)
                .withPaymentProvider(WORLDPAY.getName())
                .insert();
        
        var credentialExternalId = liveAccount.getCredentials().getFirst().getExternalId();

        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, LIVE, credentialExternalId))
                .then()
                .statusCode(SC_NOT_FOUND)
                .body("message", is("Credential is not associated with payment provider Adyen"));
    }
    
    @Test
    void getTasksShouldReturnNotFoundResponseWhenCredentialsRecordDoesNotBelongToGatewayAccount() {
         app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId(serviceId)
                .withPaymentProvider(ADYEN.getName())
                .insert();
        
        var worldpayAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withServiceId("worldpay-service")
                .withPaymentProvider(WORLDPAY.getName())
                .insert();

        var worldPayCredentialExternalId = worldpayAccount.getCredentials().getFirst().getExternalId();

        app.givenSetup()
                .get(format("/v1/api/service/%s/account/%s/adyen-setup/%s", serviceId, TEST, worldPayCredentialExternalId))
                .then()
                .statusCode(SC_NOT_FOUND);
    }
    
    private void markTasksAsCompleted(long gatewayAccountId, long credentialId, List<AdyenAccountSetupTask> tasks) {
        tasks.forEach(task -> app.getDatabaseTestHelper().addGatewayAccountsAdyenSetupTask(gatewayAccountId, credentialId, task, COMPLETED));
    }
}
