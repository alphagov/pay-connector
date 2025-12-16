package uk.gov.pay.connector.it.resources;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.exception.ValidationExceptionMapper;
import uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator;
import uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountResource;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountServicesFactory;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountSwitchPaymentProviderService;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(DropwizardExtensionsSupport.class)
class GatewayAccountResourceTest {

    private static final GatewayAccountService gatewayAccountService = mock(GatewayAccountService.class);
    private static final CardTypeDao cardTypeDao = mock(CardTypeDao.class);
    private static final GatewayAccountRequestValidator gatewayAccountRequestValidator = mock(GatewayAccountRequestValidator.class);
    private static final GatewayAccountServicesFactory gatewayAccountServicesFactory = mock(GatewayAccountServicesFactory.class);
    private static final GatewayAccountSwitchPaymentProviderService gatewayAccountSwitchPaymentProviderService = mock(GatewayAccountSwitchPaymentProviderService.class);

    public static ResourceExtension resources = ResourceExtension.builder()
            .addResource(new GatewayAccountResource(
                    gatewayAccountService,
                    cardTypeDao,
                    gatewayAccountRequestValidator,
                    gatewayAccountServicesFactory,
                    gatewayAccountSwitchPaymentProviderService
            ))
            .setRegisterDefaultExceptionMappers(false)
            .addProvider(ConstraintViolationExceptionMapper.class)
            .addProvider(ValidationExceptionMapper.class)
            .build();

    @Test
    void searchGatewayAccount_invalidAccountIdsList_shouldReturn422() {
        Response response = resources.target("/v1/api/accounts")
                .queryParam("accountIds", "123,abc")
                .request()
                .get();

        assertThat(response.getStatus(), is(422));
        assertThat(extractErrorMessagesFromResponse(response).getFirst(), is("Parameter [accountIds] must be a comma separated list of numbers"));
    }

    private List extractErrorMessagesFromResponse(Response response) {
        var responseBody = response.readEntity(new GenericType<HashMap>() {
        });
        return (List) responseBody.get("message");
    }
}
