package com.rbkmoney.scheduledpayoutworker.util;

public class GenerateUtil {

    public static String generatePayoutScheduleId(String partyId, String shopId, int scheduleId) {
        return String.join("-","payouter", partyId, shopId, String.valueOf(scheduleId));
    }

    private GenerateUtil() {
        throw new UnsupportedOperationException("Unable to instantiate utility class!");
    }
}
