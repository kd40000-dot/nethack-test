package com.tbd.NetHack5.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Keeps a user-supplied OpenAI API key encrypted with a non-exportable
 * Android Keystore key. Never include an API key in source or CI artifacts.
 * Minimum supported Android version: 6.0 (API 23).
 */
final class GuideKeyStore {
    private static final String ALIAS = "nethack_guide_openai_v1";
    private static final String PREFS = "nethack_guide_private";
    private static final String SECRET = "encrypted_openai_key";

    private GuideKeyStore() {}

    static boolean hasKey(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(SECRET);
    }

    static void forget(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(SECRET).apply();
    }

    static void save(Context context, String apiKey) throws Exception {
        if (Build.VERSION.SDK_INT < 23) {
            throw new IllegalStateException("Android 6.0 or newer is required");
        }
        if (apiKey == null || !apiKey.trim().startsWith("sk-")) {
            throw new IllegalArgumentException("Enter a valid OpenAI API key beginning with sk-");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));
        String value = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS,
                Context.MODE_PRIVATE).edit();
        if (!editor.putString(SECRET, value).commit()) {
            throw new IllegalStateException("Could not save API key");
        }
    }

    static String load(Context context) throws Exception {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(SECRET, null);
        if (stored == null) {
            throw new IllegalStateException("No API key configured");
        }
        String[] parts = stored.split(":", -1);
        if (parts.length != 2) throw new IllegalStateException("Invalid key storage");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        if (iv.length != 12) throw new IllegalStateException("Invalid key IV");
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry entry = store.getEntry(ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
}
