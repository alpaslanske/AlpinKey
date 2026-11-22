package alp.alpinsense.webauthn.util;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import alp.alpinsense.webauthn.models.PublicKeyCredentialSource;
import alp.alpinsense.webauthn.util.database.CredentialDatabase;

public class CredentialBackupManager {

    private static final String TAG = "CredentialBackupManager";
    private static final int SALT_SIZE_BYTES = 16;
    private static final int IV_SIZE_BYTES = 12;
    private static final int KEY_SIZE_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 10000;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
            .create();

    private static class ByteArrayToBase64TypeAdapter
            implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return Base64.decode(json.getAsString(), Base64.NO_WRAP);
        }

        @Override
        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(Base64.encodeToString(src, Base64.NO_WRAP));
        }
    }

    public static byte[] createEncryptedBackup(List<PublicKeyCredentialSource> credentials, String password)
            throws Exception {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }

        String json = gson.toJson(credentials);
        byte[] plaintext = json.getBytes("UTF-8");

        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_SIZE_BYTES];
        byte[] iv = new byte[IV_SIZE_BYTES];
        random.nextBytes(salt);
        random.nextBytes(iv);

        SecretKey secretKey = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(salt);
        baos.write(iv);
        baos.write(ciphertext);
        return baos.toByteArray();
    }

    public static List<PublicKeyCredentialSource> decryptBackup(byte[] backupData, String password)
            throws Exception {
        if (backupData == null || backupData.length < SALT_SIZE_BYTES + IV_SIZE_BYTES + 16) {
            throw new IllegalArgumentException("Invalid backup file data");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }

        byte[] salt = Arrays.copyOfRange(backupData, 0, SALT_SIZE_BYTES);
        byte[] iv = Arrays.copyOfRange(backupData, SALT_SIZE_BYTES, SALT_SIZE_BYTES + IV_SIZE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(backupData, SALT_SIZE_BYTES + IV_SIZE_BYTES, backupData.length);

        SecretKey secretKey = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        String json = new String(plaintext, "UTF-8");

        Type listType = new TypeToken<List<PublicKeyCredentialSource>>() {}.getType();
        return gson.fromJson(json, listType);
    }

    public static int restoreCredentialsToDatabase(Context context, List<PublicKeyCredentialSource> restoredList)
            throws Exception {
        if (restoredList == null || restoredList.isEmpty()) {
            return 0;
        }

        CredentialSafe safe = new CredentialSafe(context);
        CredentialDatabase db = CredentialDatabase.getDatabase(context);
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        int restoredCount = 0;
        for (PublicKeyCredentialSource credential : restoredList) {
            if (credential.id == null || credential.id.length == 0) {
                continue;
            }

            if (credential.keyPairAlias != null && !keyStore.containsAlias(credential.keyPairAlias)) {
                try {
                    safe.generateNewES256KeyPair(credential.keyPairAlias);
                } catch (Exception e) {
                    Log.w(TAG, "Could not generate key pair for alias: " + credential.keyPairAlias, e);
                }
            }

            if (credential.generateHmacSecret && credential.hmacSecretAlias != null
                    && !keyStore.containsAlias(credential.hmacSecretAlias)) {
                try {
                    KeyGenParameterSpec.Builder hmacParameterSpec = new KeyGenParameterSpec.Builder(
                            credential.hmacSecretAlias, KeyProperties.PURPOSE_SIGN);
                    hmacParameterSpec.setDigests(KeyProperties.DIGEST_SHA256);
                    hmacParameterSpec.setKeySize(256);
                    safe.generateSymmetricKey(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, hmacParameterSpec);
                } catch (Exception e) {
                    Log.w(TAG, "Could not generate HMAC key for alias: " + credential.hmacSecretAlias, e);
                }
            }

            PublicKeyCredentialSource existing = db.credentialDao().getById(credential.id);
            if (existing != null) {
                credential.roomUid = existing.roomUid;
                db.credentialDao().update(credential);
            } else {
                credential.roomUid = 0;
                db.credentialDao().insert(credential);
            }
            restoredCount++;
        }
        return restoredCount;
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
