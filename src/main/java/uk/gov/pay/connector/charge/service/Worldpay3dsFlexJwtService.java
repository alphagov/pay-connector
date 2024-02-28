package uk.gov.pay.connector.charge.service;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexJwtCredentialsException;
import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexJwtPaymentProviderException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.util.JwtGenerator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

public class Worldpay3dsFlexJwtService {

    private final JwtGenerator jwtGenerator;
    private final Duration tokenExpiryDurationSeconds;
    private final LinksConfig linksConfig;

    @Inject
    public Worldpay3dsFlexJwtService(JwtGenerator jwtGenerator,
                                     ConnectorConfiguration config) {
        this.jwtGenerator = jwtGenerator;
        this.tokenExpiryDurationSeconds = config.getChargeSweepConfig().getDefaultChargeExpiryThreshold();
        this.linksConfig = config.getLinks();
    }

    /**
     * Utility method to create a JWT for Worldpay 3DS Flex DDC based upon the required claims
     * shown in their documentation.
     *
     * @see <a href="https://beta.developer.worldpay.com/docs/wpg/directintegration/3ds2#device-data-collection-ddc-"
     * >Worldpay DDC Documentation</a>
     */
    public String generateDdcToken(GatewayAccount gatewayAccount, Worldpay3dsFlexCredentials worldpay3dsFlexCredentials,
                                   Instant chargeCreatedTime, String paymentProvider) {
        validateGatewayIsWorldpay(gatewayAccount, paymentProvider);

        var claims = generateDdcClaims(gatewayAccount, worldpay3dsFlexCredentials, chargeCreatedTime);
        return createJwt(gatewayAccount, worldpay3dsFlexCredentials, claims);
    }

    public Optional<String> generateChallengeTokenIfAppropriate(ChargeEntity chargeEntity) {
        if (shouldGenerateChallengeToken(chargeEntity)) {
            return Optional.of(generateChallengeToken(chargeEntity));
        }
        return Optional.empty();
    }
    
    private boolean shouldGenerateChallengeToken(ChargeEntity chargeEntity) {
        return chargeEntity.getStatus().equals(ChargeStatus.AUTHORISATION_3DS_REQUIRED.toString()) &&
                chargeEntity.getChargeCardDetails().get3dsRequiredDetails() != null &&
                chargeEntity.getChargeCardDetails().get3dsRequiredDetails().getWorldpayChallengeAcsUrl() != null &&
                chargeEntity.getChargeCardDetails().get3dsRequiredDetails().getWorldpayChallengePayload() != null &&
                chargeEntity.getChargeCardDetails().get3dsRequiredDetails().getWorldpayChallengeTransactionId() != null;
    }

    private String generateChallengeToken(ChargeEntity chargeEntity) {
        GatewayAccount gatewayAccount = GatewayAccount.valueOf(chargeEntity.getGatewayAccount());
        var worldpay3dsFlexCredentials = chargeEntity.getGatewayAccount().getWorldpay3dsFlexCredentials()
                .orElseThrow(() -> new Worldpay3dsFlexJwtCredentialsException(gatewayAccount.getId()));

        validateGatewayIsWorldpay(gatewayAccount, chargeEntity.getPaymentProvider());

        var claims = generateChallengeClaims(chargeEntity, gatewayAccount, worldpay3dsFlexCredentials);
        return createJwt(gatewayAccount, worldpay3dsFlexCredentials, claims);
    }

    private void validateGatewayIsWorldpay(GatewayAccount gatewayAccount, String paymentProvider) {
        if (!WORLDPAY.getName().equals(paymentProvider)) {
            throw new Worldpay3dsFlexJwtPaymentProviderException(gatewayAccount.getId());
        }
    }
    
    private Map<String, Object> generateDdcClaims(GatewayAccount gatewayAccount, Worldpay3dsFlexCredentials worldpay3dsFlexCredentials, Instant chargeCreatedTime) {
        Map<String, Object> commonClaims = generateCommonClaims(gatewayAccount.getId(), worldpay3dsFlexCredentials);
        Map<String, Object> claims = new HashMap<>(commonClaims);
        claims.put("exp", chargeCreatedTime.plus(tokenExpiryDurationSeconds).getEpochSecond());
        return claims;
    }

    private Map<String, Object> generateChallengeClaims(ChargeEntity chargeEntity, GatewayAccount gatewayAccount, Worldpay3dsFlexCredentials worldpay3dsFlexCredentials) {
        Map<String, Object> commonClaims = generateCommonClaims(gatewayAccount.getId(), worldpay3dsFlexCredentials);
        Map<String, Object> claims = new HashMap<>(commonClaims);
        claims.put("ReturnUrl", format("%s/card_details/%s/3ds_required_in", linksConfig.getFrontendUrl(), chargeEntity.getExternalId()));
        claims.put("ObjectifyPayload", true);
        claims.put("Payload", Map.of(
                "ACSUrl", chargeEntity.getChargeCardDetails().get3dsRequiredDetails().getWorldpayChallengeAcsUrl(),
                "Payload", chargeEntity.getChargeCardDetails().get3dsRequiredDetails().getWorldpayChallengePayload(),
                "TransactionId", chargeEntity.getChargeCardDetails().get3dsRequiredDetails().getWorldpayChallengeTransactionId()));
        return claims;
    }

    private Map<String, Object> generateCommonClaims(Long gatewayAccountId, Worldpay3dsFlexCredentials worldpay3dsFlexCredentials) {
        String issuer = Optional.ofNullable(worldpay3dsFlexCredentials.getIssuer())
                .orElseThrow(() -> new Worldpay3dsFlexJwtCredentialsException(gatewayAccountId, "issuer"));

        String organisationalUnitId = Optional.ofNullable(worldpay3dsFlexCredentials.getOrganisationalUnitId())
                .orElseThrow(() -> new Worldpay3dsFlexJwtCredentialsException(gatewayAccountId, "organisational_unit_id"));

        return Map.of(
                "jti", RandomIdGenerator.newId(),
                "iat", Instant.now().getEpochSecond(),
                "iss", issuer,
                "OrgUnitId", organisationalUnitId);
    }

    private String createJwt(GatewayAccount gatewayAccount, Worldpay3dsFlexCredentials worldpay3dsFlexCredentials, Map<String, Object> claims) {
        String jwtMacKey = Optional.ofNullable(worldpay3dsFlexCredentials.getJwtMacKey())
                .orElseThrow(() -> new Worldpay3dsFlexJwtCredentialsException(gatewayAccount.getId(), "jwt_mac_key"));
        return jwtGenerator.createJwt(claims, jwtMacKey);
    }
}
