package uk.gov.pay.connector.gateway.model.request.records;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.BDDMockito.given;
import static uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture.aCardAuthorisationGatewayRequest;
import static uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthoriseRequestFixture.aWorldpayMotoAuthoriseRequestFixture;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class AuthoriseRequestFactoryTest {

    @Mock
    private WorldpayAuthoriseRequestFactory mockWorldpayAuthoriseRequestFactory;

    private AuthoriseRequestFactory authoriseRequestFactory;

    @BeforeEach
    void setUp() {
        authoriseRequestFactory = new AuthoriseRequestFactory(mockWorldpayAuthoriseRequestFactory);
    }

    @Test
    void shouldBuildWorldpayAuthoriseRequestIfWorldpay() {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(PaymentGatewayName.WORLDPAY.toString())
                .build();

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity))
                .build();

        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withGatewayAccount(gatewayAccountEntity)
                .build();

        WorldpayAuthoriseRequest worldpayAuthoriseRequest = aWorldpayMotoAuthoriseRequestFixture().build();

        given(mockWorldpayAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest))
                .willReturn(Optional.of(worldpayAuthoriseRequest));

        Optional<? extends AuthoriseRequest> authoriseRequest = authoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(authoriseRequest.isPresent(), is(true));
        assertThat(authoriseRequest.get(), is(worldpayAuthoriseRequest));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentGatewayName.class,  mode = EXCLUDE, names = { "WORLDPAY" })
    void shouldBuildNothingIfNotWorldpay(PaymentGatewayName paymentGatewayName) {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(paymentGatewayName.toString())
                .build();

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity))
                .build();

        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withGatewayAccount(gatewayAccountEntity)
                .build();

        Optional<? extends AuthoriseRequest> authoriseRequest = authoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(authoriseRequest.isEmpty(), is(true));
    }

}
