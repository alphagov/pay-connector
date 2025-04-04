package uk.gov.pay.connector.gatewayaccount.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Person;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountCreateParams.BusinessProfile;
import com.stripe.param.AccountCreateParams.Capabilities;
import com.stripe.param.AccountCreateParams.Capabilities.CardPayments;
import com.stripe.param.AccountCreateParams.Capabilities.Transfers;
import com.stripe.param.AccountCreateParams.Settings;
import com.stripe.param.AccountCreateParams.Settings.Payouts.Schedule;
import com.stripe.param.AccountCreateParams.TosAcceptance;
import com.stripe.param.PersonCollectionCreateParams;
import com.stripe.param.PersonCollectionCreateParams.Dob;
import com.stripe.param.PersonCollectionCreateParams.Relationship;
import com.stripe.param.PersonCollectionCreateParams.Verification.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;

import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import java.util.Map;
import java.util.Optional;

import static com.stripe.param.AccountCreateParams.Company;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

public class StripeAccountService {

    private static final Logger logger = LoggerFactory.getLogger(StripeAccountService.class);
    
    private final RequestOptions requestOptions;
    
    @Inject
    public StripeAccountService(StripeGatewayConfig stripeGatewayConfig) {
        requestOptions = RequestOptions.builder().setApiKey(stripeGatewayConfig.getAuthTokens().getTest()).build();
    }

    public Optional<StripeAccountResponse> buildStripeAccountResponse(GatewayAccountEntity gatewayAccountEntity) {
        return Optional.ofNullable(gatewayAccountEntity.getGatewayAccountCredentialsEntity(STRIPE.getName()))
                .map(credentials -> ((StripeCredentials)credentials.getCredentialsObject()).getStripeAccountId())
                .map(StripeAccountResponse::new);
    }

    public Account createTestAccount(String serviceName) {
        var accountCreateParams = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.CUSTOM)
                .setCountry("GB")
                .setBusinessType(AccountCreateParams.BusinessType.COMPANY)
                .setCompany(StripeTestAccountDefaults.companyBuilder.setName(serviceName).build())
                .setCapabilities(StripeTestAccountDefaults.capabilities)
                .setDefaultCurrency("GBP")
                .setSettings(StripeTestAccountDefaults.settings)
                .setBusinessProfile(StripeTestAccountDefaults.businessProfile)
                .setTosAcceptance(StripeTestAccountDefaults.tosAcceptance)
                .build();

        try {
            Account account = Account.create(accountCreateParams, requestOptions);
            logger.info("Created account {}", account.getId());
            account.getExternalAccounts().create(StripeTestAccountDefaults.bankAccount, requestOptions);
            return account;
        } catch (StripeException e) {
            throw new InternalServerErrorException(e);
        }
    }
    
    public void createDefaultPersonForAccount(String stripeAccountId) {
        try {
            Account account = Account.retrieve(stripeAccountId, requestOptions);
            Person person = account.persons(Map.of(), requestOptions).create(StripeTestAccountDefaults.person, requestOptions);
            logger.info("Created person {}", person.getId());
        } catch (StripeException e) {
            throw new InternalServerErrorException(e);
        }
    }
    
    public static class StripeTestAccountDefaults {
        
        public static final Map<String, Object> bankAccount = Map.of("external_account",
                Map.of("account_number", "00012345",
                        "routing_number", "108800",
                        "account_holder_type", "individual",
                        "account_holder_name", "Jane Doe",
                        "object", "bank_account",
                        "currency", "gbp",
                        "country", "gb"));

        public static final Company.Builder companyBuilder = Company.builder()
                .setAddress(Company.Address.builder()
                        .setLine1("address_full_match")
                        .setLine2("WCB")
                        .setCity("London")
                        .setPostalCode("E1 8QS")
                        .setCountry("GB").build())
                .setPhone("+441212345678")
                .setTaxId("000000000")
                .setVatId("NOTAPPLI")
                .setOwnersProvided(true)
                .setDirectorsProvided(true)
                .setExecutivesProvided(true);

        public static final Capabilities capabilities = Capabilities.builder()
                .setCardPayments(CardPayments.builder().setRequested(true).build())
                .setTransfers(Transfers.builder().setRequested(true).build())
                .build();

        public static final Settings settings = Settings.builder()
                .setPayouts(Settings.Payouts.builder()
                        .setSchedule(Schedule.builder()
                                .setInterval(Schedule.Interval.DAILY)
                                .setDelayDays(7L).build())
                        .setStatementDescriptor("TEST ACCOUNT").build())
                .build();

        public static final BusinessProfile businessProfile = BusinessProfile.builder()
                .setMcc("9399")
                .setProductDescription("Test account for a service")
                .build();

        public static final TosAcceptance tosAcceptance = TosAcceptance.builder()
                .setIp("0.0.0.0")
                .setDate(System.currentTimeMillis() / 1000).build();

        public static final PersonCollectionCreateParams person = PersonCollectionCreateParams.builder()
                .setFirstName("Jane")
                .setLastName("Doe")
                .setDob(Dob.builder().setDay(1L).setMonth(1L).setYear(1901L).build())
                .setRelationship(Relationship.builder().setRepresentative(true).setExecutive(true).setTitle("CEO").build())
                .setAddress(PersonCollectionCreateParams.Address.builder()
                        .setLine1("address_full_match")
                        .setLine2("WCB")
                        .setCity("London")
                        .setPostalCode("E1 8QS")
                        .setCountry("GB").
                        build())
                .setPhone("8888675309")
                .setEmail("test@example.org")
                .setVerification(PersonCollectionCreateParams.Verification.builder()
                        .setDocument(Document.builder().setFront("file_identity_document_success").build())
                        .build())
                .build();
    }
}
