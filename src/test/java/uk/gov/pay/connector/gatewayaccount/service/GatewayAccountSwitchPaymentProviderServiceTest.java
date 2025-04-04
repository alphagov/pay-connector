package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ENTERED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

@ExtendWith(MockitoExtension.class)
class GatewayAccountSwitchPaymentProviderServiceTest {

    private GatewayAccountSwitchPaymentProviderService gatewayAccountSwitchPaymentProviderService;
    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountSwitchPaymentProviderRequest request;

    @Mock
    private GatewayAccountCredentialsDao mockGatewayAccountCredentialsDao;

    @Mock
    private GatewayAccountDao mockGatewayAccountDao;

    @BeforeEach
    void setUp() {
        gatewayAccountSwitchPaymentProviderService = new GatewayAccountSwitchPaymentProviderService(mockGatewayAccountDao, mockGatewayAccountCredentialsDao);
        gatewayAccountEntity = aGatewayAccountEntity().build();
        request = new GatewayAccountSwitchPaymentProviderRequest(randomUuid(), randomUuid());
    }

    @Nested
    class switchPaymentProviderForAccount {
        @Test
        void shouldThrowExceptionWhenMultipleActiveCredentialsArePresent() {
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(
                    aGatewayAccountCredentialsEntity()
                            .withState(ACTIVE)
                            .build(),
                    aGatewayAccountCredentialsEntity()
                            .withState(ACTIVE)
                            .build(),
                    aGatewayAccountCredentialsEntity()
                            .withState(VERIFIED_WITH_LIVE_PAYMENT)
                            .build()
                    ));
            var thrown = assertThrows(BadRequestException.class, () -> gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, null));
            assertThat(thrown.getMessage(), is("Multiple ACTIVE credentials found"));
        }

        @Test
        void shouldThrowExceptionWhenCredentialIsMissing() {
            var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                    .withState(ACTIVE)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));
            var thrown = assertThrows(BadRequestException.class, () -> gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, null));
            assertThat(thrown.getMessage(), is("Account has no credential to switch to/from"));
        }

        @Test
        void shouldThrowExceptionWhenNoActiveCredentialFound() {
            var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                    .withState(VERIFIED_WITH_LIVE_PAYMENT)
                    .build();
            var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                    .withState(RETIRED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));
            var thrown = assertThrows(BadRequestException.class, () -> gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, request));
            assertThat(thrown.getMessage(), is("Credential with ACTIVE state not found"));
        }

        @Test
        void shouldThrowExceptionWhenCredentialNonExistent() {
            var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                    .withState(ACTIVE)
                    .build();
            var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                    .withState(VERIFIED_WITH_LIVE_PAYMENT)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));
            var thrown = assertThrows(NotFoundException.class, () -> gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, request));
            assertThat(thrown.getMessage(), is(format("Credential with external id [%s] not found", request.getGatewayAccountCredentialExternalId())));
        }

        @Test
        void shouldThrowExceptionWhenCredentialNotCorrectState() {
            var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                    .withState(ACTIVE)
                    .build();
            var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                    .withExternalId(request.getGatewayAccountCredentialExternalId())
                    .withState(ENTERED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));
            var thrown = assertThrows(BadRequestException.class, () -> gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, request));
            assertThat(thrown.getMessage(), is("Credential with VERIFIED_WITH_LIVE_PAYMENT state not found"));
        }

        @Test
        void shouldSwitchPaymentProvider() {
            var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                    .withState(ACTIVE)
                    .build();
            var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                    .withExternalId(request.getGatewayAccountCredentialExternalId())
                    .withState(VERIFIED_WITH_LIVE_PAYMENT)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));
            gatewayAccountEntity.setDescription("I am a Stripe live account");


            ArgumentCaptor<List<GatewayAccountCredentialsEntity>> credentialArgumentCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<GatewayAccountEntity> gatewayArgumentCaptor = ArgumentCaptor.forClass(GatewayAccountEntity.class);

            gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, request);

            verify(mockGatewayAccountCredentialsDao, times(1)).mergeInSequence(credentialArgumentCaptor.capture());
            verify(mockGatewayAccountDao, times(1)).merge(gatewayArgumentCaptor.capture());

            List<GatewayAccountCredentialsEntity> credentialList = credentialArgumentCaptor.getValue();

            Optional<GatewayAccountCredentialsEntity> activeCredential = credentialList.stream().filter(credential -> ACTIVE.equals(credential.getState())).findFirst();
            assertThat(activeCredential.get().getExternalId(), is(gatewayAccountCredentialsEntity2.getExternalId()));

            Optional<GatewayAccountCredentialsEntity> retiredCredential = credentialList.stream().filter(credential -> RETIRED.equals(credential.getState())).findFirst();
            assertThat(retiredCredential.get().getExternalId(), is(gatewayAccountCredentialsEntity1.getExternalId()));

            GatewayAccountEntity gatewayAccount = gatewayArgumentCaptor.getValue();
            assertThat(gatewayAccount.getDescription(), is("I am a Worldpay live account"));
        }

        @ParameterizedTest
        @MethodSource("provideParamsForGatewayDescription")
        void shouldCorrectlyUpdateGatewayDescriptionWhenSwitching(PaymentGatewayName paymentGatewayNameFrom, PaymentGatewayName paymentGatewayNameTo, String gatewayDescription, String expectedDescription) {
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(
                    aGatewayAccountCredentialsEntity()
                            .withPaymentProvider(paymentGatewayNameFrom.getName())
                            .withState(ACTIVE)
                            .build(),
                    aGatewayAccountCredentialsEntity()
                            .withPaymentProvider(paymentGatewayNameTo.getName())
                            .withExternalId(request.getGatewayAccountCredentialExternalId())
                            .withState(VERIFIED_WITH_LIVE_PAYMENT)
                            .build()));
            gatewayAccountEntity.setDescription(gatewayDescription);

            ArgumentCaptor<GatewayAccountEntity> gatewayArgumentCaptor = ArgumentCaptor.forClass(GatewayAccountEntity.class);

            gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, request);

            verify(mockGatewayAccountDao, times(1)).merge(gatewayArgumentCaptor.capture());

            GatewayAccountEntity gatewayAccount = gatewayArgumentCaptor.getValue();
            assertThat(gatewayAccount.getDescription(), is(expectedDescription));
        }

        private static Stream<Arguments> provideParamsForGatewayDescription() {
            return Stream.of(
                    Arguments.of(PaymentGatewayName.STRIPE, PaymentGatewayName.WORLDPAY, "I am a Stripe live account", "I am a Worldpay live account"),
                    Arguments.of(PaymentGatewayName.WORLDPAY, PaymentGatewayName.STRIPE, "I am a woRlDPay live account", "I am a Stripe live account"),
                    Arguments.of(PaymentGatewayName.STRIPE, PaymentGatewayName.WORLDPAY, "I am a STRIPE live account", "I am a Worldpay live account"),
                    Arguments.of(PaymentGatewayName.STRIPE, PaymentGatewayName.WORLDPAY, "I am a tiger stripes live account", "I am a tiger stripes live account"),
                    Arguments.of(PaymentGatewayName.WORLDPAY, PaymentGatewayName.STRIPE, "I am a worldpaying live account", "I am a worldpaying live account"),
                    Arguments.of(PaymentGatewayName.WORLDPAY, PaymentGatewayName.STRIPE, "", ""),
                    Arguments.of(PaymentGatewayName.WORLDPAY, PaymentGatewayName.STRIPE, null, null)
            );
        }
    }

    @Nested
    class revertStripeTestAccountToSandbox {
        @Test
        void shouldRevertStripeTestGatewayWithPreExistingActiveSandboxCredential() {
            var sandboxTestCredential = aGatewayAccountCredentialsEntity()
                    .withState(ACTIVE)
                    .withPaymentProvider(PaymentGatewayName.SANDBOX.getName())
                    .build();

            var stripeTestCredential = aGatewayAccountCredentialsEntity()
                    .withState(ACTIVE)
                    .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(stripeTestCredential, sandboxTestCredential));
            gatewayAccountEntity.setDescription("I am a Stripe test account");

            ArgumentCaptor<List<GatewayAccountCredentialsEntity>> credentialArgumentCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<GatewayAccountEntity> gatewayArgumentCaptor = ArgumentCaptor.forClass(GatewayAccountEntity.class);

            gatewayAccountSwitchPaymentProviderService.revertStripeTestAccountToSandbox(gatewayAccountEntity, request);

            verify(mockGatewayAccountCredentialsDao, times(1)).mergeInSequence(credentialArgumentCaptor.capture());
            verify(mockGatewayAccountDao, times(1)).merge(gatewayArgumentCaptor.capture());

            List<GatewayAccountCredentialsEntity> credentialList = credentialArgumentCaptor.getValue();

            assertThat(credentialList.size(), is(1));

            Optional<GatewayAccountCredentialsEntity> retiredCredential = credentialList.stream().filter(credential -> RETIRED.equals(credential.getState())).findFirst();
            assertThat(retiredCredential.get().getExternalId(), is(stripeTestCredential.getExternalId()));

            GatewayAccountEntity gatewayAccount = gatewayArgumentCaptor.getValue();
            assertThat(gatewayAccount.getDescription(), is("I am a Sandbox test account"));
        }

        @Test
        void shouldRevertStripeTestGatewayToSandbox() {
            var stripeTestCredential = aGatewayAccountCredentialsEntity()
                    .withState(ACTIVE)
                    .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(stripeTestCredential));
            gatewayAccountEntity.setDescription("I am a Stripe test account");

            ArgumentCaptor<GatewayAccountCredentialsEntity> credentialArgumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
            ArgumentCaptor<List<GatewayAccountCredentialsEntity>> credentialsArgumentCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<GatewayAccountEntity> gatewayArgumentCaptor = ArgumentCaptor.forClass(GatewayAccountEntity.class);

            gatewayAccountSwitchPaymentProviderService.revertStripeTestAccountToSandbox(gatewayAccountEntity, request);

            verify(mockGatewayAccountCredentialsDao, times(1)).merge(credentialArgumentCaptor.capture());
            verify(mockGatewayAccountCredentialsDao, times(1)).mergeInSequence(credentialsArgumentCaptor.capture());
            verify(mockGatewayAccountDao, times(1)).merge(gatewayArgumentCaptor.capture());

            List<GatewayAccountCredentialsEntity> allCredentials = new ArrayList<>();
            allCredentials.add(credentialArgumentCaptor.getValue());
            allCredentials.addAll(credentialsArgumentCaptor.getValue());

            Optional<GatewayAccountCredentialsEntity> activeCredential = allCredentials.stream().filter(credential -> ACTIVE.equals(credential.getState())).findFirst();
            assertThat(activeCredential.get().getPaymentProvider(), is(PaymentGatewayName.SANDBOX.getName()));

            Optional<GatewayAccountCredentialsEntity> retiredCredential = allCredentials.stream().filter(credential -> RETIRED.equals(credential.getState())).findFirst();
            assertThat(retiredCredential.get().getExternalId(), is(stripeTestCredential.getExternalId()));

            GatewayAccountEntity gatewayAccount = gatewayArgumentCaptor.getValue();
            assertThat(gatewayAccount.getDescription(), is("I am a Sandbox test account"));
        }

        @ParameterizedTest
        @MethodSource("provideParamsForGatewayDescription")
        void shouldCorrectlyUpdateGatewayDescriptionWhenRevertingStripeTestAccount(String gatewayDescription, String expectedDescription) {
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(
                    aGatewayAccountCredentialsEntity()
                            .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                            .withState(ACTIVE)
                            .build()));
            gatewayAccountEntity.setType(GatewayAccountType.TEST);
            gatewayAccountEntity.setDescription(gatewayDescription);

            ArgumentCaptor<GatewayAccountEntity> gatewayArgumentCaptor = ArgumentCaptor.forClass(GatewayAccountEntity.class);

            gatewayAccountSwitchPaymentProviderService.revertStripeTestAccountToSandbox(gatewayAccountEntity, request);

            verify(mockGatewayAccountDao, times(1)).merge(gatewayArgumentCaptor.capture());

            GatewayAccountEntity gatewayAccount = gatewayArgumentCaptor.getValue();
            assertThat(gatewayAccount.getDescription(), is(expectedDescription));
        }

        private static Stream<Arguments> provideParamsForGatewayDescription() {
            return Stream.of(
                    Arguments.of("I am a Stripe test account", "I am a Sandbox test account"),
                    Arguments.of("I am a STRIPE test account", "I am a Sandbox test account"),
                    Arguments.of("I am a STriPe test account", "I am a Sandbox test account"),
                    Arguments.of("order your pinstripes service TEST", "order your pinstripes service TEST"),
                    Arguments.of("", ""),
                    Arguments.of(null, null)
            );
        }

        @Test
        void shouldThrowExceptionWhenAccountIsNotTest() {
            var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                    .withState(ACTIVE)
                    .build();
            gatewayAccountEntity.setType(GatewayAccountType.LIVE);
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1));
            var thrown = assertThrows(IllegalArgumentException.class, () -> gatewayAccountSwitchPaymentProviderService.revertStripeTestAccountToSandbox(gatewayAccountEntity, request));
            assertThat(thrown.getMessage(), is(format("Gateway account cannot be live [gateway account id: %s]", gatewayAccountEntity.getId())));
        }

        @Test
        void shouldThrowExceptionWhenAccountCredentialIsNotStripe() {
            var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                    .withState(ACTIVE)
                    .build();
            gatewayAccountEntity.setType(GatewayAccountType.TEST);
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1));
            var thrown = assertThrows(BadRequestException.class, () -> gatewayAccountSwitchPaymentProviderService.revertStripeTestAccountToSandbox(gatewayAccountEntity, request));
            assertThat(thrown.getMessage(), is(format("Stripe credential with ACTIVE state not found for account [gateway account id: %s]", gatewayAccountEntity.getId())));
        }
    }
}
