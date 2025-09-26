package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class StripePaymentMethodRequestTest {
    private final String stripeConnectAccountId = "stripeConnectAccountId";
    private final String chargeExternalId = "payChargeExternalId";
    private final String stripeBaseUrl = "stripeUrl";
    private final String cardNo = "4242424242424242";
    private final String cardHolder = "Jones";
    private final String line1 = "line1";
    private final String line2 = "line2";
    private final String city = "city";
    private final String country = "UK";
    private final String postcode = "W21WE";
    private String cvc = "123";
    private String endMonth = "12";
    private String endYear = "19";

    private StripePaymentMethodRequest stripePaymentMethodRequest;

    @Mock
    ChargeEntity charge;

    GatewayAccountEntity gatewayAccount;
    @Mock
    StripeAuthTokens stripeAuthTokens;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;
    private AuthCardDetails authCardDetails;

    @BeforeEach
    void setUp() {
        gatewayAccount = aGatewayAccountEntity()
                .withGatewayName("stripe")
                .build();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", stripeConnectAccountId))
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        when(charge.getGatewayAccount()).thenReturn(gatewayAccount);
        when(charge.getExternalId()).thenReturn(chargeExternalId);
        when(charge.getGatewayAccountCredentialsEntity()).thenReturn(gatewayAccountCredentialsEntity);
        when(charge.getReference()).thenReturn(ServicePaymentReference.of("a-reference"));

        authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo(cardNo);
        authCardDetails.setCardHolder(cardHolder);
        authCardDetails.setCvc(cvc);
        authCardDetails.setEndDate(CardExpiryDate.valueOf(endMonth + "/" + endYear));

        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, authCardDetails);

        stripePaymentMethodRequest = StripePaymentMethodRequest.of(authorisationGatewayRequest, stripeGatewayConfig);
    }
    
    @Test
    void shouldHaveCorrectParametersWithAddress() {
        Address address = new Address();
        address.setLine1(line1);
        address.setLine2(line2);
        address.setCity(city);
        address.setCountry(country);
        address.setPostcode(postcode);
        authCardDetails.setAddress(address);
        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, authCardDetails);

        stripePaymentMethodRequest = StripePaymentMethodRequest.of(authorisationGatewayRequest, stripeGatewayConfig);

        String payload = stripePaymentMethodRequest.getGatewayOrder().getPayload();
        assertThat(payload, containsString("card%5Bcvc%5D=" + cvc));
        assertThat(payload, containsString("card%5Bnumber%5D=" + cardNo));
        assertThat(payload, containsString("billing_details%5Bname%5D=" + cardHolder));
        assertThat(payload, containsString("card%5Bexp_year%5D=" + endYear));
        assertThat(payload, containsString("card%5Bexp_month%5D=" + endMonth));
        assertThat(payload, containsString("billing_details%5Baddress%5Bline1%5D%5D=" + line1));
        assertThat(payload, containsString("billing_details%5Baddress%5Bline2%5D%5D=" + line2));
        assertThat(payload, containsString("billing_details%5Baddress%5Bcity%5D%5D=" + city));
        assertThat(payload, containsString("billing_details%5Baddress%5Bcountry%5D%5D=" + country));
        assertThat(payload, containsString("billing_details%5Baddress%5Bpostal_code%5D%5D=" + postcode));
    }
    
    @Test
    void shouldHaveCanadianProvinceOrTerritoryInBillingDetailsWhenProvided() {
        Address address = new Address();
        address.setLine1(line1);
        address.setLine2(line2);
        address.setCity(city);
        address.setCountry("CA");
        address.setPostcode("X0A0A0");
        authCardDetails.setAddress(address);
        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, authCardDetails);

        stripePaymentMethodRequest = StripePaymentMethodRequest.of(authorisationGatewayRequest, stripeGatewayConfig);

        String payload = stripePaymentMethodRequest.getGatewayOrder().getPayload();

        assertThat(payload, containsString("billing_details%5Baddress%5Bstate%5D%5D=Nunavut"));
        assertThat(payload, containsString("billing_details%5Baddress%5Bcountry%5D%5D=CA"));
        assertThat(payload, containsString("billing_details%5Baddress%5Bpostal_code%5D%5D=X0A0A0"));
    }

    @Test
    void shouldHaveUsStateInBillingDetailsWhenProvided() {
        Address address = new Address();
        address.setLine1(line1);
        address.setLine2(line2);
        address.setCity(city);
        address.setCountry("US");
        address.setPostcode("90210");
        authCardDetails.setAddress(address);
        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, authCardDetails);

        stripePaymentMethodRequest = StripePaymentMethodRequest.of(authorisationGatewayRequest, stripeGatewayConfig);

        String payload = stripePaymentMethodRequest.getGatewayOrder().getPayload();

        assertThat(payload, containsString("billing_details%5Baddress%5Bstate%5D%5D=California"));
        assertThat(payload, containsString("billing_details%5Baddress%5Bcountry%5D%5D=US"));
        assertThat(payload, containsString("billing_details%5Baddress%5Bpostal_code%5D%5D=90210"));
    }

    @Test
    void shouldHaveCorrectParametersWithoutAddress() {
        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        stripePaymentMethodRequest = StripePaymentMethodRequest.of(authorisationGatewayRequest, stripeGatewayConfig);
        
        String payload = stripePaymentMethodRequest.getGatewayOrder().getPayload();
        assertThat(payload, containsString("card%5Bcvc%5D=" + cvc));
        assertThat(payload, containsString("card%5Bnumber%5D=" + cardNo));
        assertThat(payload, containsString("billing_details%5Bname%5D=" + cardHolder));
        assertThat(payload, containsString("card%5Bexp_year%5D=" + endYear));
        assertThat(payload, containsString("card%5Bexp_month%5D=" + endMonth));
    }
    
    @Test
    void shouldCreateWithPartiallyFieldCardHolderDetail() {
        Address address = new Address();
        address.setLine1(line1);
        address.setLine2(line2);
        address.setCity(null);
        address.setCountry(null);
        address.setPostcode(null);
        authCardDetails.setAddress(address);
        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, authCardDetails);

        stripePaymentMethodRequest = StripePaymentMethodRequest.of(authorisationGatewayRequest, stripeGatewayConfig);

        String payload = stripePaymentMethodRequest.getGatewayOrder().getPayload();
        assertThat(payload, containsString("card%5Bcvc%5D=" + cvc));
        assertThat(payload, not(containsString("city")));
        assertThat(payload, not(containsString("country")));
        assertThat(payload, not(containsString("postal_code")));
    }

    @Test
    void createsCorrectIdempotencyKey() {
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);

        assertThat(
                stripePaymentMethodRequest.getHeaders().get("Idempotency-Key"),
                is("payment_method" + chargeExternalId));
    }

    @Test
    void shouldCreateCorrectUrl() {
        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);

        assertThat(stripePaymentMethodRequest.getUrl(), is(URI.create(stripeBaseUrl + "/v1/payment_methods")));
    }
}
