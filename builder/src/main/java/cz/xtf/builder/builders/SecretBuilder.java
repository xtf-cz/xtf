package cz.xtf.builder.builders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import cz.xtf.builder.builders.secret.SecretType;
import io.fabric8.kubernetes.api.model.Secret;

public class SecretBuilder extends AbstractBuilder<Secret, SecretBuilder> {
    private SecretType type = SecretType.OPAQUE;
    private Map<String, String> data = new HashMap<>();

    public SecretBuilder(String id) {
        this(null, id);
    }

    SecretBuilder(ApplicationBuilder applicationBuilder, String id) {
        super(applicationBuilder, id);
    }

    public SecretBuilder addData(String key, Path path) throws IOException {
        return addData(key, Files.newInputStream(path));
    }

    public SecretBuilder addData(String key, InputStream data) {
        return addData(key, getBytes(data));
    }

    public SecretBuilder addData(String key, byte[] data) {
        return addEncodedData(key, Base64.getEncoder().encodeToString(data));
    }

    /**
     * Use this to add raw (unencoded) data to the secret.
     */
    public SecretBuilder addRawData(String key, String data) {
        return addEncodedData(key, Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Use this to add data already encoded in Base64 to the secret.
     */
    public SecretBuilder addEncodedData(String key, String data) {
        this.data.put(key, data);
        return this;
    }

    public SecretBuilder setType(SecretType type) {
        this.type = type;
        return this;
    }

    @Override
    public Secret build() {
        if (data.size() == 0) {
            throw new IllegalStateException("secret data cannot be empty");
        }

        return new io.fabric8.kubernetes.api.model.SecretBuilder()
                .withMetadata(metadataBuilder().build())
                .withType(type.toString())
                .withData(data)
                .build();
    }

    @Override
    protected SecretBuilder getThis() {
        return this;
    }

    /*
     * Private methods
     */
    private byte[] getBytes(final InputStream is) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            IOUtils.copy(is, os);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not process data stream", e);
        }
        return os.toByteArray();
    }

}
