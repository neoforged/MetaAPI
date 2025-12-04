package net.neoforged.meta.db;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * JPA converter that automatically compresses/decompresses string data using deflate.
 */
@Converter
public class DeflateConverter implements AttributeConverter<String, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        try (var baos = new ByteArrayOutputStream();
             var dos = new DeflaterOutputStream(baos)) {
            dos.write(attribute.getBytes(StandardCharsets.UTF_8));
            dos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to compress data", e);
        }
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }

        try (var iis = new InflaterInputStream(new ByteArrayInputStream(dbData));
             var baos = new ByteArrayOutputStream()) {

            iis.transferTo(baos);

            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decompress data", e);
        }
    }
}
