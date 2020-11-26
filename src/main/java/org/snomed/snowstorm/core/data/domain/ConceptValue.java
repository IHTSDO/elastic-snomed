package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.annotation.Transient;

/**
 * Represent the Value of a Concrete Relationship
 * in API response.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonView(value = View.Component.class)
public final class ConceptValue {
    @Transient
    private final ConceptValue.DataType dataType;

    @Transient
    private final String value;

    public enum DataType {
        DECIMAL("DECIMAL"),
        INTEGER("INTEGER"),
        STRING("STRING");

        private final String value;

        DataType(final String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
    }

    public ConceptValue(String value) {
        if (value == null || value.length() < 2) {
            throw new IllegalArgumentException("Value cannot be null.");
        }

        final boolean isConcreteNumber = value.startsWith("#");
        final boolean isConcreteString = value.startsWith("\"");

        if (!isConcreteNumber && !isConcreteString) {
            throw new IllegalArgumentException(value + " is not a concrete value.");
        }

        DataType dataType = null;
        if (isConcreteString) {
            dataType = DataType.STRING;
        } else {
            value = value.replaceFirst("#", "");
            try {
                Integer.parseInt(value);
                dataType = DataType.INTEGER;
            } catch (final NumberFormatException e) {
                //ignore
            }

            if (dataType == null) {
                try {
                    Double.parseDouble(value);
                    dataType = DataType.DECIMAL;
                } catch (final NumberFormatException e) {
                    //ignore
                }
            }
        }

        this.dataType = dataType;
        this.value = value;
    }

    public ConceptValue.DataType getDataType() {
        return this.dataType;
    }

    public String getValue() {
        return this.value;
    }
}
