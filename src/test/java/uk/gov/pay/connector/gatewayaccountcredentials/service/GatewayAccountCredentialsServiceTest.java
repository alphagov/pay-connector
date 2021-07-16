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
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.NoCredentialsExistForProviderException;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.NoCredentialsInUsableStateException;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ENTERED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
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
    void createCredentialsForSandboxShouldCreateRecordWithActiveState() {
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
    void createCredentialsForStripeAndWithCredentialsShouldCreateRecordWithActiveState() {
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
    void createCredentialsForStripeShouldCreateRecordWithEnteredStateIfAnActiveGatewayAccountCredentialExists() {
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
    void createCredentialsForStripeAndWithOutCredentialsShouldCreateRecordWithCreatedState() {
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
    void createCredentialsForProvidersShouldCreateRecordWithCreatedState(String paymentProvider) {
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
    void shouldUpdateGatewayAccountCredentials() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(CREATED)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));


        JsonNode replaceCredentialsNode = objectMapper.valueToTree(
                Map.of("path", "credentials",
                        "op", "replace",
                        "value", Map.of(
                                "merchant_id", "new-merchant-id"
                        )));
        JsonNode replaceLastUserNode = objectMapper.valueToTree(
                Map.of("path", "last_updated_by_user_external_id",
                        "op", "replace",
                        "value", "new-user-external-id")
        );
        JsonNode replaceStateNode = objectMapper.valueToTree(
                Map.of("path", "state",
                        "op", "replace",
                        "value", "VERIFIED_WITH_LIVE_PAYMENT")
        );

        List<JsonPatchRequest> patchRequests = Stream.of(replaceCredentialsNode, replaceLastUserNode, replaceStateNode)
                .map(JsonPatchRequest::from)
                .collect(Collectors.toList());

        gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, patchRequests);

        verify(mockGatewayAccountCredentialsDao).merge(credentialsEntity);
        assertThat(credentialsEntity.getCredentials(), hasEntry("merchant_id", "new-merchant-id"));
        assertThat(credentialsEntity.getLastUpdatedByUserExternalId(), is("new-user-external-id"));
        assertThat(credentialsEntity.getState(), is(VERIFIED_WITH_LIVE_PAYMENT));
    }

    @Test
    void shouldChangeStateToActive_whenCredentialsInCreatedStateUpdated_andOnlyOneCredential() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(CREATED)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

        JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                Map.of("path", "credentials",
                        "op", "replace",
                        "value", Map.of(
                                "merchant_id", "new-merchant-id"
                        ))));
        gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, Collections.singletonList(patchRequest));
        
        assertThat(credentialsEntity.getState(), is(ACTIVE));
    }

    @Test
    void shouldChangeStateToEntered_whenCredentialsInCreatedStateUpdated_andMoreThanOneCredential() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsBeingUpdated = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(CREATED)
                .build();
        GatewayAccountCredentialsEntity otherCredentials = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsBeingUpdated, otherCredentials));

        JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                Map.of("path", "credentials",
                        "op", "replace",
                        "value", Map.of(
                                "merchant_id", "new-merchant-id"
                        ))));
        gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsBeingUpdated, Collections.singletonList(patchRequest));

        assertThat(credentialsBeingUpdated.getState(), is(ENTERED));
        assertThat(otherCredentials.getState(), is(ACTIVE));
    }

    @Test
    void shouldNotChangeState_whenCredentialsNotInCreatedState() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(VERIFIED_WITH_LIVE_PAYMENT)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

        JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                Map.of("path", "credentials",
                        "op", "replace",
                        "value", Map.of(
                                "merchant_id", "new-merchant-id"
                        ))));
        gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, Collections.singletonList(patchRequest));

        assertThat(credentialsEntity.getState(), is(VERIFIED_WITH_LIVE_PAYMENT));
    }

    @Test
    void shouldThrowForNoCredentialsForProvider() {
        GatewayAccountCredentialsEntity credentials = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("smartpay").withState(ENTERED).build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(Collections.singletonList(credentials)).build();

        NoCredentialsExistForProviderException exception = assertThrows(NoCredentialsExistForProviderException.class,
                () -> gatewayAccountCredentialsService.getUsableCredentialsForProvider(gatewayAccountEntity, "worldpay"));
        assertThat(exception.getMessage(), is("Account does not support payment provider [worldpay]"));
    }

    @Test
    void shouldThrowForNoCredentialsInUsableState() {
        GatewayAccountCredentialsEntity credentials = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay").withState(CREATED).build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(Collections.singletonList(credentials)).build();

        NoCredentialsInUsableStateException exception = assertThrows(NoCredentialsInUsableStateException.class,
                () -> gatewayAccountCredentialsService.getUsableCredentialsForProvider(gatewayAccountEntity, "worldpay"));
        assertThat(exception.getMessage(), is("Payment provider details are not configured on this account"));
    }


    @Test
    void shouldThrowIfMultipleUsableCredentials() {
        GatewayAccountCredentialsEntity credentials1 = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay").withState(ENTERED).build();
        GatewayAccountCredentialsEntity credentials2 = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay").withState(VERIFIED_WITH_LIVE_PAYMENT).build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(credentials1, credentials2)).build();

        assertThrows(WebApplicationException.class,
                () -> gatewayAccountCredentialsService.getUsableCredentialsForProvider(gatewayAccountEntity, "worldpay"));
    }

    @Test
    void shouldThrowForCredentialsOnlyInCreatedState() {
        GatewayAccountCredentialsEntity credentials = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay").withState(CREATED).build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(Collections.singletonList(credentials)).build();

        NoCredentialsInUsableStateException exception = assertThrows(NoCredentialsInUsableStateException.class,
                () -> gatewayAccountCredentialsService.getUsableCredentialsForProvider(gatewayAccountEntity, "worldpay"));
        assertThat(exception.getMessage(), is("Payment provider details are not configured on this account"));
    }

    @Test
    void shouldReturnSingleUsableCredentialForPaymentProvider() {
        GatewayAccountCredentialsEntity credentials1 = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay").withState(ENTERED).build();
        GatewayAccountCredentialsEntity credentials2 = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay").withState(CREATED).build();
        GatewayAccountCredentialsEntity credentials3 = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay").withState(RETIRED).build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(credentials1, credentials2, credentials3)).build();

        GatewayAccountCredentialsEntity result = gatewayAccountCredentialsService.getUsableCredentialsForProvider(gatewayAccountEntity, credentials1.getPaymentProvider());
        assertThat(result, is(credentials1));
    }

    @Test
    void shouldReturnStripeGatewayAccountForGatewayAccountCredentials() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(STRIPE.getName())
                .build();
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withCredentials(Map.of("stripe_account_id", "stripeAccountId"))
                .withPaymentProvider(STRIPE.getName())
                .build();

        when(mockGatewayAccountCredentialsDao.findByCredentialsKeyValue(eq("stripe_account_id"), eq("stripeAccountId"))).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

        gatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue("stripe_account_id", "stripeAccountId");

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
    }

    @Test
    void shouldThrowRuntimeExceptionIfGatewayAccountNotFound() {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", "stripeAccountId"))
                .withPaymentProvider(STRIPE.getName())
                .build();

        when(mockGatewayAccountCredentialsDao.findByCredentialsKeyValue(eq("stripe_account_id"), eq("stripeAccountId"))).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

        assertThrows(GatewayAccountNotFoundException.class,
                () -> gatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue("stripe_account_id", "stripeAccountId"));
    }

    @Test
    void shouldThrowRuntimeExceptionIfGatewayAccountCredentialsNotFound() {
        when(mockGatewayAccountCredentialsDao.findByCredentialsKeyValue(eq("stripe_account_id"), eq("stripeAccountId"))).thenReturn(Optional.empty());

        assertThrows(GatewayAccountCredentialsNotFoundException.class,
                () -> gatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue("stripe_account_id", "stripeAccountId"));
    }

    @Test
    void shouldThrowNoCredentialsInUsableStateExceptionIfGatewayAccountCredentialInCreatedState() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withState(CREATED)
                .build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
            .withCredentials(null)
            .withGatewayAccountCredentials(List.of(credentialsEntityWorldpay))
            .build();

        assertThrows(NoCredentialsInUsableStateException.class,
                () -> gatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccountEntity));
    }

    @Test
    void shouldThrowWebApplicationExceptionIfNoCredentialsFound() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withCredentials(null)
                .withGatewayAccountCredentials(List.of())
                .build();

        assertThrows(WebApplicationException.class,
                () -> gatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccountEntity));
    }
}
