package com.it.greendeer.obs;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OBS v5 鉴权算法测试：对照独立实现的摘要链。
 */
class ObsAuthTest {

    @Test
    void authMatchesIndependentImplementation() throws Exception {
        String password = "mypassword";
        String salt = "salt123";
        String challenge = "challenge456";

        String expected = independentAuth(password, salt, challenge);
        Method m = ObsClient.class.getDeclaredMethod("computeAuth", String.class, String.class, String.class);
        m.setAccessible(true);
        String actual = (String) m.invoke(null, password, challenge, salt);
        assertEquals(expected, actual);
    }

    private String independentAuth(String password, String salt, String challenge) throws Exception {
        String secret = b64(sha256((password + salt).getBytes(StandardCharsets.UTF_8)));
        return b64(sha256((secret + challenge).getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] in) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(in);
    }

    private static String b64(byte[] in) {
        return Base64.getEncoder().encodeToString(in);
    }
}
