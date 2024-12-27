package uk.gov.pay.connector.cardtype.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.common.model.domain.UuidAbstractEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "card_types")
public class CardTypeEntity extends UuidAbstractEntity {

    @Column
    @JsonProperty
    @Schema(example = "visa")
    private String brand;

    @Column
    @JsonProperty
    @Schema(example = "Visa")
    private String label;

    @Column
    @Enumerated(EnumType.STRING)
    @Schema(example = "DEBIT")
    private CardType type;

    @Column(name = "requires_3ds")
    @JsonProperty
    @Schema(name= "requires3ds")
    private boolean requires3ds;

    @Override
    @JsonIgnore
    public Long getVersion() {
        return super.getVersion();
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public CardType getType() {
        return type;
    }

    public void setType(CardType cardType) {
        this.type = cardType;
    }

    public boolean isRequires3ds() {
        return requires3ds;
    }

    public void setRequires3ds(boolean requires3ds) {
        this.requires3ds = requires3ds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardTypeEntity that = (CardTypeEntity) o;
        return requires3ds == that.requires3ds &&
                Objects.equals(brand, that.brand) &&
                Objects.equals(label, that.label) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(brand, label, type, requires3ds);
    }
}
