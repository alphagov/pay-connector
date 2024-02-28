package uk.gov.pay.connector.gateway.epdq.model;

import uk.gov.pay.connector.card.model.Auth3dsRequiredEntity;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;

public class Epdq3dsRequiredParams implements Gateway3dsRequiredParams {

    private final String htmlOut;

    public Epdq3dsRequiredParams(String htmlOut) {
        this.htmlOut = htmlOut;
    }

    @Override
    public Auth3dsRequiredEntity toAuth3dsRequiredEntity() {
        Auth3dsRequiredEntity auth3DsRequiredEntity = new Auth3dsRequiredEntity();
        auth3DsRequiredEntity.setHtmlOut(htmlOut);
        return auth3DsRequiredEntity;
    }
}
