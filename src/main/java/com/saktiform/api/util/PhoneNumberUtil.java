package com.saktiform.api.util;

public class PhoneNumberUtil {

    public static String normalizeToIndonesianFormat(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }

        phone = phone.trim();

        // 1️⃣ Hilangkan tanda +
        if (phone.startsWith("+")) {
            phone = phone.substring(1);
        }

        // 2️⃣ Hapus semua selain angka
        phone = phone.replaceAll("[^0-9]", "");

        // 3️⃣ Handle 6208xxxx → 628xxxx
        if (phone.startsWith("620")) {
            phone = "62" + phone.substring(3);
        }

        // 4️⃣ Hilangkan double 62 (6262xxxx)
        while (phone.startsWith("6262")) {
            phone = phone.substring(2);
        }

        // 5️⃣ Handle 08xxxx → 628xxxx
        if (phone.startsWith("0")) {
            phone = "62" + phone.substring(1);
        }

        return phone;
    }

    public static String extractPhoneNumber(String input) {
        if (input == null || !input.contains("@")) {
            return input;
        }
        return input.substring(0, input.indexOf("@"));
    }

}

