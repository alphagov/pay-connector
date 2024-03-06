package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.base.NewChargingITestBase;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.AddGatewayAccountParams;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargesApiResourceCreateProviderCredentialsIT extends NewChargingITestBase {

    public ChargesApiResourceCreateProviderCredentialsIT() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldCreateChargeForCredentialIdProvided() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountCredentialsParams credentialsToUse = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider("worldpay")
                .withState(GatewayAccountCredentialState.ENTERED)
                .withGatewayAccountId(Long.parseLong(accountId))
                .build();
        AddGatewayAccountCredentialsParams activeCredentials = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider("worldpay")
                .withState(GatewayAccountCredentialState.ACTIVE)
                .withGatewayAccountId(Long.parseLong(accountId))
                .build();
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withGatewayAccountCredentials(List.of(credentialsToUse, activeCredentials))
                .withAccountId(accountId)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_CREDENTIAL_ID_KEY, credentialsToUse.getExternalId()
        ));

        String chargeId = connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_PROVIDER_KEY, is("worldpay"))
                .extract().body().jsonPath().get("charge_id");

        Map<String, Object> charge = databaseTestHelper.getChargeByExternalId(chargeId);
        assertThat(charge.get("gateway_account_credential_id"), is(credentialsToUse.getId()));
    }

    @Test
    public void shouldReturn400WhenCredentialsNotFoundForCredentialIdProvided() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountCredentialsParams credentials = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider("worldpay")
                .withState(GatewayAccountCredentialState.ACTIVE)
                .withGatewayAccountId(Long.parseLong(accountId))
                .build();
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withGatewayAccountCredentials(List.of(credentials))
                .withAccountId(accountId)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_CREDENTIAL_ID_KEY, "random-credential-id"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()))
                .body("message", contains(format("Credentials not found for gateway account [%s] and credential_external_id [random-credential-id]", accountId)));
    }

    @Test
    public void shouldReturn400WhenNoCredentialsAreInUsableState() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountCredentialsParams credentials = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider("worldpay")
                .withState(GatewayAccountCredentialState.RETIRED)
                .withGatewayAccountId(Long.parseLong(accountId))
                .build();
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withGatewayAccountCredentials(List.of(credentials))
                .withAccountId(accountId)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_CREDENTIAL_ID_KEY, credentials.getExternalId()
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains("Payment provider details are not configured on this account"))
                .body("error_identifier", is(ErrorIdentifier.ACCOUNT_NOT_LINKED_WITH_PSP.toString()));
    }

    @Test
    public void shouldReturn400WhenCredentialsInCreatedState() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountCredentialsParams credentials = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider("worldpay")
                .withState(GatewayAccountCredentialState.CREATED)
                .withGatewayAccountId(Long.parseLong(accountId))
                .build();
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withGatewayAccountCredentials(List.of(credentials))
                .withAccountId(accountId)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains("Payment provider details are not configured on this account"))
                .body("error_identifier", is(ErrorIdentifier.ACCOUNT_NOT_LINKED_WITH_PSP.toString()));
    }

}
