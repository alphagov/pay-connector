package uk.gov.pay.connector.it.resources;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.exception.ValidationExceptionMapper;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gatewayaccount.model.CreateGatewayAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountRequest;
import uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountResource;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(DropwizardExtensionsSupport.class)
@ExtendWith(MockitoExtension.class)
class GatewayAccountResourceTest {

    private static final GatewayAccountService gatewayAccountService = mock(GatewayAccountService.class);

    private static final ResourceExtension resources = ResourceExtension.builder()
            .addResource(new GatewayAccountResource(
                    gatewayAccountService,
                    null,
                    null,
                    null,
                    null
            ))
            .setRegisterDefaultExceptionMappers(false)
            .addProvider(ConstraintViolationExceptionMapper.class)
            .addProvider(ValidationExceptionMapper.class)
            .build();

    @Captor
    ArgumentCaptor<GatewayAccountRequest> gatewayAccountRequestCaptor;

    @Test
    void searchGatewayAccount_invalidAccountIdsList_shouldReturn422() {
        var invalidAccountIdsList = "123,abc";

        var response = resources.target("/v1/api/accounts")
                .queryParam("accountIds", invalidAccountIdsList)
                .request()
                .get();

        assertThat(response.getStatus(), is(422));

        assertTrue(response.readEntity(ErrorResponse.class).messages()
                .contains("Parameter [accountIds] must be a comma separated list of numbers"));
    }

    @Test
    void should_create_gateway_account_on_create_gateway_account_request() {
        given(gatewayAccountService.createGatewayAccount(any(), any()))
                .willReturn(new CreateGatewayAccountResponse.GatewayAccountResponseBuilder().build());

        resources.target("/v1/api/accounts")
                .request()
                .post(Entity.json("""
                        {
                          "payment_provider": "stripe",
                          "type": "test",
                          "service_name": "new service",
                          "service_id": "service-external-id",
                          "analytics_id": "analytics-id",
                          "requires_3ds": true,
                          "allow_apple_pay": true,
                          "allow_google_pay": true,
                          "send_payer_email_to_gateway": true,
                          "send_payer_ip_address_to_gateway": true
                        }
                        """));

        then(gatewayAccountService).should()
                .createGatewayAccount(gatewayAccountRequestCaptor.capture(), any());
        GatewayAccountRequest gatewayAccountRequest = gatewayAccountRequestCaptor.getValue();

        assertThat(gatewayAccountRequest.getPaymentProvider(), is("stripe"));
        assertThat(gatewayAccountRequest.getProviderAccountType(), is("test"));
        assertThat(gatewayAccountRequest.getServiceName(), is("new service"));
        assertThat(gatewayAccountRequest.getServiceId(), is("service-external-id"));
        assertThat(gatewayAccountRequest.getAnalyticsId(), is("analytics-id"));
        assertTrue(gatewayAccountRequest.isRequires3ds());
        assertTrue(gatewayAccountRequest.isAllowApplePay());
        assertTrue(gatewayAccountRequest.isAllowGooglePay());
        assertTrue(gatewayAccountRequest.isSendPayerEmailToGateway());
        assertTrue(gatewayAccountRequest.isSendPayerIpAddressToGateway());
    }
}
