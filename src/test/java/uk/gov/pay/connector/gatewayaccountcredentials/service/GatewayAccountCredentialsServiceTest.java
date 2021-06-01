package uk.gov.pay.connector.gatewayaccountcredentials.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;

@ExtendWith(MockitoExtension.class)
public class GatewayAccountCredentialsServiceTest {

    @Mock
    GatewayAccountCredentialsDao mockGatewayAccountCredentialsDao;

    GatewayAccountCredentialsService gatewayAccountCredentialsService;

    @BeforeEach
    void setup() {
        gatewayAccountCredentialsService = new GatewayAccountCredentialsService(mockGatewayAccountCredentialsDao);
    }

    @Test
    public void createCredentialsForSandboxShouldCreateRecordWithActiveState() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();

        ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "sandbox", Map.of());

        verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("sandbox"));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        assertThat(gatewayAccountCredentialsEntity.getState(), is(ACTIVE));
        assertThat(gatewayAccountCredentialsEntity.getCredentials().isEmpty(), is(true));
    }

    @Test
    public void createCredentialsForStripeAndWithCredentialsShouldCreateRecordWithActiveState() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();

        ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "stripe", Map.of("stripe_account_id", "abc"));

        verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        assertThat(gatewayAccountCredentialsEntity.getState(), is(ACTIVE));
        assertThat(gatewayAccountCredentialsEntity.getCredentials().get("stripe_account_id"), is("abc"));
    }

    @Test
    public void createCredentialsForStripeAndWithOutCredentialsShouldCreateRecordWithCreatedState() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();

        ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "stripe", Map.of());

        verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        assertThat(gatewayAccountCredentialsEntity.getState(), is(CREATED));
        assertThat(gatewayAccountCredentialsEntity.getCredentials().isEmpty(), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"worldpay", "epdq", "smartpay"})
    public void createCredentialsForProvidersShouldCreateRecordWithCreatedState(String paymentProvider) {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();

        ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, paymentProvider, Map.of());

        verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is(paymentProvider));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        assertThat(gatewayAccountCredentialsEntity.getState(), is(CREATED));
        assertThat(gatewayAccountCredentialsEntity.getCredentials().isEmpty(), is(true));
    }
}
