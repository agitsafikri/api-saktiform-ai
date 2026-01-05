package com.saktiform.api.util;

public class PhoneNumberUtil {

    public static String normalizeToIndonesianFormat(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }

        phone = phone.trim();

        if (phone.startsWith("62")) {
            return phone; // sudah sesuai
        } else if (phone.startsWith("0")) {
            return "62" + phone.substring(1);
        }else if(phone.startsWith("+62")){
            return phone.substring(1);
        } else {
            // kalau format aneh, langsung balikin apa adanya
            return phone;
        }
    }

    public static String extractPhoneNumber(String input) {
        if (input == null || !input.contains("@")) {
            return input;
        }
        return input.substring(0, input.indexOf("@"));
    }

}

