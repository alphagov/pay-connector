package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;

import javax.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;


class GatewayAccountEntityTest {
    
    private GatewayAccountEntity gatewayAccountEntity;
    private List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntities = new ArrayList<>();
    
    @BeforeEach
    void setUp() {
        gatewayAccountEntity = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .withGatewayAccountCredentials(gatewayAccountCredentialsEntities)
                .build();
    }

    @Test
    void shouldReturnSandboxAsPaymentProviderNameForSingleGatewayAccountCredentialEntity() {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity().build();
        gatewayAccountCredentialsEntities.add(gatewayAccountCredentialsEntity);
        assertThat(gatewayAccountEntity.getGatewayNameFromGatewayAccountCredentials(), is("worldpay"));
    }

    @Test
    void shouldReturnStripeAsPaymentProviderNameForLatestGatewayAccountCredentialEntity() {
        GatewayAccountCredentialsEntity latestActiveGatewayAccountCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("stripe")
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();

        GatewayAccountCredentialsEntity earlierActiveGatewayAccountCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("sandbox")
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();

        GatewayAccountCredentialsEntity latestRetiredGatewayAccountCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("sandbox")
                .withState(GatewayAccountCredentialState.RETIRED)
                .build();

        latestActiveGatewayAccountCredential.setActiveStartDate(Instant.parse("2021-08-04T10:00:00Z"));
        earlierActiveGatewayAccountCredential.setActiveStartDate(Instant.parse("2021-01-03T10:00:00Z"));
        latestRetiredGatewayAccountCredential.setActiveStartDate(Instant.parse("2021-09-04T10:00:00Z"));
        gatewayAccountCredentialsEntities.add(latestActiveGatewayAccountCredential);
        gatewayAccountCredentialsEntities.add(earlierActiveGatewayAccountCredential);
        gatewayAccountCredentialsEntities.add(latestRetiredGatewayAccountCredential);

        assertThat(gatewayAccountEntity.getGatewayNameFromGatewayAccountCredentials(), is("stripe"));
    }

    @Test
    void shouldThrowWebApplicationExceptionWhenGatewayAccountCredentialsIsEmpty() {
        assertThrows(WebApplicationException.class, () -> gatewayAccountEntity.getGatewayNameFromGatewayAccountCredentials());
    }
}
