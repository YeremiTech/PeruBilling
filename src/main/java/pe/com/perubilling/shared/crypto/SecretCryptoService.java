package pe.com.perubilling.shared.crypto;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretCryptoService {
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String configuredKey;
    private SecretKey key;

    public SecretCryptoService(@Value("${app.security.master-key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @PostConstruct
    void init() {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException("MASTER_KEY es obligatorio. Use una clave Base64 de 32 bytes.");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("MASTER_KEY debe estar en Base64", ex);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("MASTER_KEY debe contener exactamente 32 bytes después de Base64");
        }
        key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        return Base64.getEncoder().encodeToString(encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    public String decrypt(String ciphertext) {
        return new String(decryptBytes(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
    }

    public byte[] encryptBytes(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo cifrar el secreto", ex);
        }
    }

    public byte[] decryptBytes(byte[] payload) {
        try {
            if (payload.length <= IV_LENGTH) throw new IllegalArgumentException("Payload cifrado inválido");
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo descifrar el secreto", ex);
        }
    }
}
