package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountResponse;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

@RunWith(MockitoJUnitRunner.class)
public class StripeAccountServiceTest {

    private StripeAccountService stripeAccountService;

    private GatewayAccountEntity gatewayAccountEntity;

    @Before
    public void setUp() {
        stripeAccountService = new StripeAccountService();
    }

    @Test
    public void buildStripeAccountResponseShouldReturnStripeAccountResponse() {
        String stripeAccountId = "acct_123example123";
        gatewayAccountEntity = aServiceAccount(Map.of("stripe_account_id", stripeAccountId));

        Optional<StripeAccountResponse> stripeAccountResponseOptional =
                stripeAccountService.buildStripeAccountResponse(gatewayAccountEntity);

        assertThat(stripeAccountResponseOptional.get().getStripeAccountId(), is(stripeAccountId));
    }

    @Test
    public void buildStripeAccountResponseShouldReturnEmptyOptionalWhenCredentialsAreEmpty() {
        gatewayAccountEntity = aServiceAccount(Map.of());

        Optional<StripeAccountResponse> stripeAccountResponseOptional =
                stripeAccountService.buildStripeAccountResponse(gatewayAccountEntity);

        assertThat(stripeAccountResponseOptional.isPresent(), is(false));
    }

    @Test
    public void buildStripeAccountResponseShouldReturnEmptyOptionalWhenCredentialsPropertyIsNull() {
        gatewayAccountEntity = aServiceAccount(Collections.singletonMap("stripe_account_id", null));

        Optional<StripeAccountResponse> stripeAccountResponseOptional =
                stripeAccountService.buildStripeAccountResponse(gatewayAccountEntity);

        assertThat(stripeAccountResponseOptional.isPresent(), is(false));
    }

    private GatewayAccountEntity aServiceAccount(Map credentials) {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName("stripe")
                .withCredentials(credentials)
                .build();

        return gatewayAccountEntity;
    }
}
