package ci.sycapay.pispi.config.converter;

import ci.sycapay.pispi.enums.CanalCommunication;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CanalCommunicationConverter implements AttributeConverter<CanalCommunication, String> {

    @Override
    public String convertToDatabaseColumn(CanalCommunication attribute) {
        return attribute != null ? attribute.getCode() : null;
    }

    @Override
    public CanalCommunication convertToEntityAttribute(String dbData) {
        return dbData != null ? CanalCommunication.fromCode(dbData) : null;
    }
}
