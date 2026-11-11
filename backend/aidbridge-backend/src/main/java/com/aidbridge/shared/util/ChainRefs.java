package com.aidbridge.shared.util;

import java.security.SecureRandom;

public class ChainRefs {
    private static final SecureRandom RANDOM = new SecureRandom();
    private ChainRefs() {
    }
    public static byte[] randomBytes32() {
        byte[] bytes = new byte[32];
        do {
            RANDOM.nextBytes(bytes);
        } while (isAllZero(bytes));
        return bytes;
    }

    private static boolean isAllZero(byte[] bytes) {
        int acc = 0;
        for (byte b : bytes) {
            acc |= b;
        }
        return acc == 0;
    }
}
