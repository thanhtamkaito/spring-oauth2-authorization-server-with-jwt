package com.benoly.auth.util;

import lombok.NonNull;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.util.SerializationUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public class TokenUtils {
    public static String serializeAuthentication(@NonNull OAuth2Authentication auth2Authentication) {
        // TODO - handle null authentication
        var authenticationByteArray = SerializationUtils.serialize(auth2Authentication);
        return Base64.getEncoder().encodeToString(authenticationByteArray);
    }

    public static OAuth2Authentication deserializeAuthentication(String authentication) {
        var authenticationBytes = Base64.getDecoder().decode(authentication);
        var deserializedAuthentication = SerializationUtils.deserialize(authenticationBytes);
        if (!(deserializedAuthentication instanceof OAuth2Authentication))
            throw new RuntimeException("invalid authentication object");

        return (OAuth2Authentication) deserializedAuthentication;
    }

    public static String generateHash(String value) {
        if (value == null) return null;
        try {
            var md5Digest = MessageDigest.getInstance("MD5");
            var tokenBytes = value.getBytes(StandardCharsets.UTF_8);
            tokenBytes = md5Digest.digest(tokenBytes);
            return String.format("%032x", new BigInteger(1, tokenBytes));

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 algorithm not available");
        }
    }

    public static MessageDigest getMessageDigestInstance(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ignored) {
            throw new RuntimeException("unable to get hash algorithm");
        }
    }

    public static byte[] hashString(String algorithm, String value) {
        return getMessageDigestInstance(algorithm)
                .digest(value.getBytes(StandardCharsets.US_ASCII));
    }

    public static Map<String, Object> toOpenIdCompliantMap(Map<String, Object> claimsMap) {
        Map<String, Object> mutableMap = new HashMap<>(claimsMap);
        mutableMap.keySet()
                .parallelStream()
                .forEach(key -> {
                    if (mutableMap.get(key) instanceof Instant) {
                        Instant instant = (Instant) mutableMap.get(key);
                        mutableMap.put(key, new Date(instant.toEpochMilli()));
                    }

                    if (mutableMap.get(key) instanceof LocalDate) {
                        LocalDate localDate = (LocalDate) mutableMap.get(key);
                        String date = localDate.toString();
                        mutableMap.put(key, date);
                    }

                    if (mutableMap.get(key) == null)
                        mutableMap.remove(key);
                });
        return mutableMap;
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }
}
