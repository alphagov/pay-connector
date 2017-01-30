package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;

import java.util.Optional;

public class Auth3dsDetailsFactory {

    public Optional<Auth3dsDetailsEntity> create(BaseAuthoriseResponse authoriseResponse) {
        if (authoriseResponse.get3dsIssuerUrl() != null && authoriseResponse.get3dsPaRequest() != null) {
            Auth3dsDetailsEntity auth3dsDetailsEntity = new Auth3dsDetailsEntity();
            auth3dsDetailsEntity.setIssuerUrl(authoriseResponse.get3dsIssuerUrl());
            auth3dsDetailsEntity.setPaRequest(authoriseResponse.get3dsPaRequest());
            return Optional.of(auth3dsDetailsEntity);
        }

        return Optional.empty();
    }
}
