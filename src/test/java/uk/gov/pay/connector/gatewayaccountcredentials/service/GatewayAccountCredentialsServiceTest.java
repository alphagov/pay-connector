package uk.gov.pay.connector.gatewayaccountcredentials.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchRequest;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ENTERED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
public class GatewayAccountCredentialsServiceTest {
    
    @Mock
    GatewayAccountCredentialsDao mockGatewayAccountCredentialsDao;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        when(mockGatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountEntity.getId())).thenReturn(false);

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
    public void createCredentialsForStripeShouldCreateRecordWithEnteredStateIfAnActiveGatewayAccountCredentialExists() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        when(mockGatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountEntity.getId())).thenReturn(true);

        ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "stripe", Map.of("stripe_account_id", "abc"));

        verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        assertThat(gatewayAccountCredentialsEntity.getState(), is(ENTERED));
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

    @Test
    public void shouldUpdateGatewayCredentialsIfExactlyOneRecordAndSetToActive() {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withState(CREATED)
                .build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity))
                .build();

        var credentials = Map.of("username", "foo");
        gatewayAccountCredentialsService.updateGatewayAccountCredentialsForLegacyEndpoint(gatewayAccountEntity, credentials);

        assertThat(gatewayAccountEntity.getGatewayAccountCredentials(), hasSize(1));
        GatewayAccountCredentialsEntity updatedCredentials = gatewayAccountEntity.getGatewayAccountCredentials().get(0);
        assertThat(updatedCredentials.getCredentials(), is(credentials));
        assertThat(updatedCredentials.getState(), is(ACTIVE));
        assertThat(updatedCredentials.getActiveStartDate(), is(notNullValue()));
    }

    @Test
    void shouldNotUpdateIfNoCredentialsRecordsExist() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withCredentials(null)
                .withGatewayAccountCredentials(List.of())
                .build();

        var credentials = Map.of("username", "foo");
        gatewayAccountCredentialsService.updateGatewayAccountCredentialsForLegacyEndpoint(gatewayAccountEntity, credentials);

        assertThat(gatewayAccountEntity.getGatewayAccountCredentials(), hasSize(0));
    }

    @Test
    void shouldThrowExceptionIfMoreThanOneCredentialsRecord() {
        GatewayAccountCredentialsEntity credentialsEntity1 = aGatewayAccountCredentialsEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity2 = aGatewayAccountCredentialsEntity().build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(credentialsEntity1, credentialsEntity2))
                .build();

        var credentials = Map.of("username", "foo");
        assertThrows(WebApplicationException.class, () ->
                gatewayAccountCredentialsService.updateGatewayAccountCredentialsForLegacyEndpoint(gatewayAccountEntity, credentials));
    }

    @Test
    void shouldUpdateGatewayAccountCredentials() {
        long credentialsId = 1;
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        when(mockGatewayAccountCredentialsDao.findById(credentialsId)).thenReturn(Optional.of(credentialsEntity));
        
        gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsId, Collections.singletonList(getValidUpdateCredentialsRequest()));
        
        assertThat(credentialsEntity.getCredentials(), hasEntry("merchant_id", "new-merchant-id"));
    }

    @Test
    void shouldThrowForNotFoundCredentialsId() {
        long credentialsId = 1;
        when(mockGatewayAccountCredentialsDao.findById(credentialsId)).thenReturn(Optional.empty());
        assertThrows(GatewayAccountCredentialsNotFoundException.class, () ->
                gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsId,
                        Collections.singletonList(getValidUpdateCredentialsRequest())));
    }
    
    private JsonPatchRequest getValidUpdateCredentialsRequest() {
        JsonNode request = objectMapper.valueToTree(
                Map.of("path", "credentials",
                        "op", "replace",
                        "value", Map.of(
                                "merchant_id", "new-merchant-id"
                        )));
        return JsonPatchRequest.from(request);
    }
}
