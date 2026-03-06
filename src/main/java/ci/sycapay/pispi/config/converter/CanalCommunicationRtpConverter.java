package ci.sycapay.pispi.config.converter;

import ci.sycapay.pispi.enums.CanalCommunicationRtp;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CanalCommunicationRtpConverter implements AttributeConverter<CanalCommunicationRtp, String> {

    @Override
    public String convertToDatabaseColumn(CanalCommunicationRtp attribute) {
        return attribute != null ? attribute.getCode() : null;
    }

    @Override
    public CanalCommunicationRtp convertToEntityAttribute(String dbData) {
        return dbData != null ? CanalCommunicationRtp.fromCode(dbData) : null;
    }
}
