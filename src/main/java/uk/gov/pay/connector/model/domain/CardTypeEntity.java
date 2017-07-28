package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "card_types")
public class CardTypeEntity extends UuidAbstractEntity {

    public enum Type {
        CREDIT,
        DEBIT
    }

    @Column
    @JsonProperty
    private String brand;

    @Column
    @JsonProperty
    private String label;

    @Column
    @Enumerated(EnumType.STRING)
    private Type type;

    @Column(name = "requires_3ds")
    @JsonProperty
    private boolean requires3ds;

    @Override
    @JsonIgnore
    public Long getVersion() {
        return super.getVersion();
    }

    public static CardTypeEntity aCardType(UUID id) {
        CardTypeEntity cardTypeEntity = new CardTypeEntity();
        cardTypeEntity.setId(id);
        return cardTypeEntity;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String externalId) {
        this.brand = externalId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isRequires3ds() {
        return requires3ds;
    }

    public void setRequires3ds(boolean requires3ds) {
        this.requires3ds = requires3ds;
    }
}
