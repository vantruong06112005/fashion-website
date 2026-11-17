package iuh.fit.fashionwebsite.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class VNPayUtil {
    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("VNPay signing key and data are required");
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();

        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create VNPay signature", ex);
        }
    }

    public static String getIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-FORWARDED-FOR");
        String ipAddress = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        return "0:0:0:0:0:0:0:1".equals(ipAddress) ? "127.0.0.1" : ipAddress;
    }

    public static String getRandomNumber(int len) {
        Random rnd = new Random();
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
    public static String getPaymentURL(Map<String, String> paramsMap, boolean encodeKey) {
        return paramsMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(entry ->
                        (encodeKey ? URLEncoder.encode(entry.getKey(),
                                StandardCharsets.UTF_8)
                                : entry.getKey()) + "=" +
                                URLEncoder.encode(entry.getValue()
                                                , StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static String hashAllFields(Map<String, String> fields, String secretKey) {
        return hmacSHA512(secretKey, getPaymentURL(new TreeMap<>(fields), false));
    }
}
