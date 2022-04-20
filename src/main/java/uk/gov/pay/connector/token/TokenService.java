package uk.gov.pay.connector.token;

import uk.gov.pay.connector.charge.exception.OneTimeTokenAlreadyUsedException;
import uk.gov.pay.connector.charge.exception.OneTimeTokenInvalidException;
import uk.gov.pay.connector.charge.exception.OneTimeTokenUsageInvalidForMotoApiException;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import javax.inject.Inject;

import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

public class TokenService {

    private final TokenDao tokenDao;

    @Inject
    public TokenService(TokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    public void validateTokenForMotoApi(String oneTimeToken) {
        tokenDao.findByTokenId(oneTimeToken)
                .map((TokenEntity tokenEntity) -> {
                    if (tokenEntity.isUsed()) {
                        throw new OneTimeTokenAlreadyUsedException();
                    }

                    if (tokenEntity.getChargeEntity() != null &&
                            tokenEntity.getChargeEntity().getAuthorisationMode() == MOTO_API) {
                        return tokenEntity;
                    }
                    throw new OneTimeTokenUsageInvalidForMotoApiException();
                })
                .orElseThrow(OneTimeTokenInvalidException::new);
    }
}
