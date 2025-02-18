package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;
import static uk.gov.service.payments.logging.LoggingKeys.USER_EXTERNAL_ID;

public class GatewayAccountSwitchPaymentProviderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAccountSwitchPaymentProviderService.class);

    private final GatewayAccountCredentialsDao gatewayAccountCredentialsDao;
    private final GatewayAccountDao gatewayAccountDao;

    @Inject
    public GatewayAccountSwitchPaymentProviderService(GatewayAccountDao gatewayAccountDao, GatewayAccountCredentialsDao gatewayAccountCredentialsDao) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.gatewayAccountCredentialsDao = gatewayAccountCredentialsDao;
    }

    @Transactional
    public void switchPaymentProviderForAccount(GatewayAccountEntity gatewayAccountEntity,
                                                GatewayAccountSwitchPaymentProviderRequest request) {

        List<GatewayAccountCredentialsEntity> credentials = gatewayAccountEntity.getGatewayAccountCredentials();

        if (credentials.size() < 2) {
            throw new BadRequestException("Account has no credential to switch to/from");
        }

        var activeCredentialEntity = findSingleCredentialEntityByState(credentials, ACTIVE);
        var switchingCredentialEntity = findSingleCredentialEntityByState(credentials, VERIFIED_WITH_LIVE_PAYMENT);

        if (!request.getGatewayAccountCredentialExternalId().equals(switchingCredentialEntity.getExternalId())) {
            throw new NotFoundException(format("Credential with external id [%s] not found", request.getGatewayAccountCredentialExternalId()));
        }

        switchingCredentialEntity.setLastUpdatedByUserExternalId(request.getUserExternalId());
        switchingCredentialEntity.setActiveStartDate(Instant.now());
        switchingCredentialEntity.setState(ACTIVE);

        activeCredentialEntity.setState(RETIRED);
        activeCredentialEntity.setLastUpdatedByUserExternalId(request.getUserExternalId());
        activeCredentialEntity.setActiveEndDate(Instant.now());

        gatewayAccountEntity.setProviderSwitchEnabled(false);
        gatewayAccountEntity.setDescription(
                replaceAllCaseInsensitive(
                        gatewayAccountEntity.getDescription(),
                        activeCredentialEntity.getPaymentProvider(),
                        switchingCredentialEntity.getPaymentProvider()
                )
        );

        gatewayAccountCredentialsDao.mergeInSequence(Arrays.asList(switchingCredentialEntity, activeCredentialEntity));
        gatewayAccountDao.merge(gatewayAccountEntity);

        LOGGER.info("Gateway account [id={}] switched to new payment provider", gatewayAccountEntity.getId(),
                kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                kv(PROVIDER, activeCredentialEntity.getPaymentProvider()),
                kv("new_payment_provider", switchingCredentialEntity.getPaymentProvider()),
                kv(USER_EXTERNAL_ID, request.getUserExternalId())
        );
    }

    @Transactional
    public void revertStripeTestAccountToSandbox(GatewayAccountEntity gatewayAccountEntity, GatewayAccountSwitchPaymentProviderRequest request) {
        if (gatewayAccountEntity.isLive()) {
            throw new IllegalArgumentException(format("Gateway account cannot be live [gateway account id: %s]", gatewayAccountEntity.getId()));
        }
        
        List<GatewayAccountCredentialsEntity> gatewayAccountCredentials = gatewayAccountEntity.getGatewayAccountCredentials();
        
        // there could be more than one active credential for a test account depending on when it was created
        var activeStripeCredentialEntities =  gatewayAccountCredentials.stream()
                .filter(this::isActiveStripeCredential)
                .collect(Collectors.toCollection(ArrayList::new));
        
        if (activeStripeCredentialEntities.isEmpty()) {
            throw new BadRequestException(format("Stripe credential with ACTIVE state not found for account [gateway account id: %s]",
                    gatewayAccountEntity.getId()));
        }

        activeStripeCredentialEntities.forEach(cred -> {
            cred.setState(RETIRED);
            cred.setLastUpdatedByUserExternalId(request.getUserExternalId());
            cred.setActiveEndDate(Instant.now());
        });

        var hasActiveSandboxCredential = gatewayAccountCredentials.stream()
                .anyMatch(this::isActiveSandboxCredential);

        gatewayAccountEntity.setDescription(
                replaceAllCaseInsensitive(
                        gatewayAccountEntity.getDescription(),
                        PaymentGatewayName.STRIPE.getName(),
                        PaymentGatewayName.SANDBOX.getName()
                )
        );

        if (!hasActiveSandboxCredential) {
            var sandboxCredentialEntity = new GatewayAccountCredentialsEntity(
                    gatewayAccountEntity,
                    PaymentGatewayName.SANDBOX.getName(),
                    Map.of(),
                    ACTIVE
            );

            sandboxCredentialEntity.setExternalId(randomUuid());
            sandboxCredentialEntity.setLastUpdatedByUserExternalId(request.getUserExternalId());
            sandboxCredentialEntity.setActiveStartDate(Instant.now());

            gatewayAccountCredentialsDao.merge(sandboxCredentialEntity);
        }
        
        gatewayAccountCredentialsDao.mergeInSequence(activeStripeCredentialEntities);
        gatewayAccountDao.merge(gatewayAccountEntity);

        LOGGER.info("Gateway account [id={}] reverted to sandbox", gatewayAccountEntity.getId(),
                kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                kv(PROVIDER, PaymentGatewayName.STRIPE.getName()),
                kv("new_payment_provider", PaymentGatewayName.SANDBOX.getName()),
                kv(USER_EXTERNAL_ID, request.getUserExternalId())
        );
    }

    private String replaceAllCaseInsensitive(String value, String target, String replacement) {
        if (value == null || target == null || replacement == null) {
            return value;
        }
        // case in-sensitive word match
        String pattern = "(?i)\\b" + Pattern.quote(target) + "\\b";
        return value.replaceAll(pattern, StringUtils.capitalize(replacement));
    }

    private GatewayAccountCredentialsEntity findSingleCredentialEntityByState(List<GatewayAccountCredentialsEntity> credentials, GatewayAccountCredentialState credentialState) {
        return credentials
                .stream()
                .filter(credential -> credentialState.equals(credential.getState()))
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> {
                            if (list.isEmpty()) {
                                throw new BadRequestException(format("Credential with %s state not found", credentialState.toString()));
                            }
                            if (list.size() > 1) {
                                throw new BadRequestException(format("Multiple %s credentials found", credentialState.toString()));
                            }
                            return list.getFirst();
                        }
                ));
    }

    private boolean isActiveSandboxCredential(GatewayAccountCredentialsEntity credential) {
        return credential.getPaymentProvider().equals(PaymentGatewayName.SANDBOX.getName()) &&
                credential.getState().equals(ACTIVE);
    }

    private boolean isActiveStripeCredential(GatewayAccountCredentialsEntity credential) {
        return credential.getPaymentProvider().equals(PaymentGatewayName.STRIPE.getName()) &&
                credential.getState().equals(ACTIVE);
    }
}
