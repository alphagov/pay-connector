package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "card_types")
public class CardTypeEntity extends UuidAbstractEntity {

    public enum Type {
        CREDIT("CREDIT"),
        DEBIT("DEBIT");

        private String value;

        Type(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public String toString() {
            return this.getValue();
        }

        public static Type fromString(String type) {
            for (Type t : values()) {
                if (StringUtils.equals(t.getValue(), type)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("type not recognized: " + type);
        }
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

    @Override
    @JsonIgnore
    public Long getVersion() {
        return super.getVersion();
    }

    public static CardTypeEntity aCardType() {
        return new CardTypeEntity();
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

}
