/**
 * @Author: Martin
 * @CreateTime: 2025-09-20
 * @Description: sha256Encrypt
 * @Version: 1.0
 */

package martin.game.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA256Utils {
    public static String sha256Encrypt(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(str.getBytes());
            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // 测试
    public static void main(String[] args) {
        String password = "123456";
        String encrypted = sha256Encrypt(password);
        System.out.println("SHA-256加密结果：" + encrypted);
        // 例如：8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92
    }
}
