package uk.gov.pay.connector.it.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.AddressFixture;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class ChargeDaoCardDetailsIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private ChargeDao chargeDao;
    private GatewayAccountDao gatewayAccountDao;
    private GatewayAccountCredentialsDao gatewayAccountCredentialsDao;

    @BeforeEach
    void setUp() {
        chargeDao = app.getInstanceFromGuiceContainer(ChargeDao.class);
        gatewayAccountDao = app.getInstanceFromGuiceContainer(GatewayAccountDao.class);
        gatewayAccountCredentialsDao = app.getInstanceFromGuiceContainer(GatewayAccountCredentialsDao.class);
    }

    private void createChargeWithIdAndDetails(long chargeId, DatabaseFixtures.TestCardDetails testCardDetails) {
        DatabaseFixtures.TestAccount testAccountFixture = app.getDatabaseFixtures()
                .aTestAccount()
                .insert();
        app.getDatabaseFixtures()
                .aTestCharge()
                .withChargeId(chargeId)
                .withCardDetails(testCardDetails)
                .withTestAccount(testAccountFixture)
                .withPaymentProvider(testAccountFixture.getPaymentProvider())
                .withChargeStatus(ChargeStatus.CAPTURE_SUBMITTED)
                .insert();
    }

    private DatabaseFixtures.TestCardDetails createCardDetailsForChargeWithId(long chargeId) {
        DatabaseFixtures.TestCardDetails testCardDetails = app.getDatabaseFixtures()
                .aTestCardDetails()
                .withChargeId(chargeId);
        createChargeWithIdAndDetails(chargeId, testCardDetails);
        return testCardDetails;
    }

    @Test
    void findById_shouldFindCardDetails() {
        long chargeId = secureRandomLong();
        DatabaseFixtures.TestCardDetails testCardDetails = createCardDetailsForChargeWithId(chargeId);
        Optional<ChargeEntity> chargeDaoOptional = chargeDao.findById(chargeId);

        assertThat(chargeDaoOptional.isPresent(), is(true));

        CardDetailsEntity cardDetailsEntity = chargeDaoOptional.get().getCardDetails();

        assertThat(cardDetailsEntity.getCardHolderName(), is(testCardDetails.getCardHolderName()));
        assertThat(cardDetailsEntity.getLastDigitsCardNumber(), is(testCardDetails.getLastDigitsCardNumber()));
        assertThat(cardDetailsEntity.getFirstDigitsCardNumber(), is(testCardDetails.getFirstDigitsCardNumber()));
        assertThat(cardDetailsEntity.getExpiryDate(), is(testCardDetails.getExpiryDate()));
        assertThat(cardDetailsEntity.getBillingAddress().isPresent(), is(true));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine1(), is(testCardDetails.getBillingAddress().getLine1()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine2(), is(testCardDetails.getBillingAddress().getLine2()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getPostcode(), is(testCardDetails.getBillingAddress().getPostcode()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCity(), is(testCardDetails.getBillingAddress().getCity()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCounty(), is(testCardDetails.getBillingAddress().getCounty()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCountry(), is(testCardDetails.getBillingAddress().getCountry()));
    }

    @Test
    void findById_shouldFindCardDetailsIfCardDigitsAreNotPresent() {
        long chargeId = secureRandomLong();
        DatabaseFixtures.TestCardDetails testCardDetails = app.getDatabaseFixtures()
                .aTestCardDetails()
                .withFirstDigitsOfCardNumber(null)
                .withLastDigitsOfCardNumber(null)
                .withChargeId(chargeId);
        createChargeWithIdAndDetails(chargeId, testCardDetails);
        Optional<ChargeEntity> chargeDaoOptional = chargeDao.findById(chargeId);
        assertThat(chargeDaoOptional.isPresent(), is(true));

        CardDetailsEntity cardDetailsEntity = chargeDaoOptional.get().getCardDetails();

        assertThat(cardDetailsEntity.getCardHolderName(), is(testCardDetails.getCardHolderName()));
        assertThat(cardDetailsEntity.getLastDigitsCardNumber(), is(nullValue()));
        assertThat(cardDetailsEntity.getFirstDigitsCardNumber(), is(nullValue()));
        assertThat(cardDetailsEntity.getExpiryDate(), is(testCardDetails.getExpiryDate()));
        assertThat(cardDetailsEntity.getBillingAddress().isPresent(), is(true));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine1(), is(testCardDetails.getBillingAddress().getLine1()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine2(), is(testCardDetails.getBillingAddress().getLine2()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getPostcode(), is(testCardDetails.getBillingAddress().getPostcode()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCity(), is(testCardDetails.getBillingAddress().getCity()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCounty(), is(testCardDetails.getBillingAddress().getCounty()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCountry(), is(testCardDetails.getBillingAddress().getCountry()));
    }

    @Test
    void persist_shouldStoreCardDetails() {
        GatewayAccountEntity testAccount = new GatewayAccountEntity(GatewayAccountType.TEST);
        testAccount.setExternalId(randomUuid());
        gatewayAccountDao.persist(testAccount);
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of())
                .withGatewayAccountEntity(testAccount)
                .withPaymentProvider(SANDBOX.getName())
                .withState(ACTIVE)
                .build();
        credentialsEntity.setExternalId(randomUuid());
        gatewayAccountCredentialsDao.persist(credentialsEntity);

        Address billingAddress = AddressFixture.anAddress().build();
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(testAccount)
                .withGatewayAccountCredentialsEntity(credentialsEntity)
                .build();
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("123456"), LastDigitsCardNumber.of("1258"),
                "Mr. Pay Mc Payment", CardExpiryDate.valueOf("03/09"), "VISA", CardType.DEBIT, new AddressEntity(billingAddress));
        chargeEntity.setCardDetails(cardDetailsEntity);
        chargeDao.persist(chargeEntity);

        Map<String, Object> cardDetailsSaved = app.getDatabaseTestHelper().getChargeCardDetails(chargeEntity.getId());
        assertThat(cardDetailsSaved, hasEntry("card_type", "DEBIT"));
    }

    @Test
    void persist_shouldStoreNullCardTypeDetails() {
        GatewayAccountEntity testAccount = new GatewayAccountEntity(GatewayAccountType.TEST);
        testAccount.setExternalId(randomUuid());
        gatewayAccountDao.persist(testAccount);
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of(
                        "username", "theUsername",
                        "password", "thePassword",
                        "merchant_id", "theMerchantCode"))
                .withGatewayAccountEntity(testAccount)
                .withPaymentProvider(SANDBOX.getName())
                .withState(ACTIVE)
                .withExternalId(randomUuid())
                .build();
        gatewayAccountCredentialsDao.persist(credentialsEntity);

        Address billingAddress = AddressFixture.anAddress().build();
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(testAccount)
                .withGatewayAccountCredentialsEntity(credentialsEntity)
                .build();
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("123456"), LastDigitsCardNumber.of("1258"),
                "Mr. Pay Mc Payment", CardExpiryDate.valueOf("03/09"), "VISA", null, new AddressEntity(billingAddress));
        chargeEntity.setCardDetails(cardDetailsEntity);
        chargeDao.persist(chargeEntity);

        Map<String, Object> cardDetailsSaved = app.getDatabaseTestHelper().getChargeCardDetails(chargeEntity.getId());
        assertThat(cardDetailsSaved, hasEntry("card_type", null));
    }
}
