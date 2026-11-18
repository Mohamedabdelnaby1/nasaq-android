package com.yawmi.diabetes.core;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Portable password-protected backups. The password is never stored. */
public final class BackupCipher {
    private static final byte[] MAGIC = "YAWMI001".getBytes(StandardCharsets.US_ASCII);
    private static final int ROUNDS = 210_000;
    private BackupCipher() {}
    private static SecretKey key(char[] pass, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(pass, salt, ROUNDS, 256);
        try { return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), "AES"); }
        finally { spec.clearPassword(); }
    }
    public static byte[] encrypt(byte[] plain, char[] pass) throws GeneralSecurityException {
        if (pass.length < 10) throw new IllegalArgumentException("Password needs at least 10 characters");
        byte[] salt = new byte[16], iv = new byte[12];
        SecureRandom random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), new GCMParameterSpec(128, iv)); c.updateAAD(MAGIC);
        byte[] encrypted = c.doFinal(plain);
        return ByteBuffer.allocate(36 + encrypted.length).put(MAGIC).put(salt).put(iv).put(encrypted).array();
    }
    public static byte[] decrypt(byte[] bytes, char[] pass) throws GeneralSecurityException {
        if (bytes.length < 52 || bytes.length > 16_000_000) throw new GeneralSecurityException("Invalid backup size");
        ByteBuffer b = ByteBuffer.wrap(bytes);
        byte[] magic = new byte[8], salt = new byte[16], iv = new byte[12]; b.get(magic); b.get(salt); b.get(iv);
        if (!MessageDigest.isEqual(MAGIC, magic)) throw new GeneralSecurityException("Unknown backup format");
        byte[] payload = new byte[b.remaining()]; b.get(payload);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(pass, salt), new GCMParameterSpec(128, iv)); c.updateAAD(MAGIC);
        return c.doFinal(payload);
    }
}
