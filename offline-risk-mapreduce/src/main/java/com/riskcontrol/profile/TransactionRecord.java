package com.riskcontrol.profile;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

final class TransactionRecord {
    private final String transId;
    private final String userId;
    private final double amount;
    private final long timestamp;
    private final String city;
    private final String deviceId;

    private TransactionRecord(String transId, String userId, double amount, long timestamp,
                              String city, String deviceId) {
        this.transId = transId;
        this.userId = userId;
        this.amount = amount;
        this.timestamp = timestamp;
        this.city = city;
        this.deviceId = deviceId;
    }

    static TransactionRecord fromJson(JsonNode node) throws IOException {
        String transId = requiredText(node, "trans_id");
        String userId = requiredText(node, "user_id");
        double amount = requiredDouble(node, "amount");
        long timestamp = requiredLong(node, "timestamp");
        String city = requiredText(node, "city");
        String deviceId = requiredText(node, "device_id");
        return new TransactionRecord(transId, userId, amount, timestamp, city, deviceId);
    }

    String getTransId() {
        return transId;
    }

    String getUserId() {
        return userId;
    }

    double getAmount() {
        return amount;
    }

    long getTimestamp() {
        return timestamp;
    }

    String getCity() {
        return city;
    }

    String getDeviceId() {
        return deviceId;
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().trim().isEmpty()) {
            throw new IOException("Missing required field: " + field);
        }
        return value.asText().trim();
    }

    private static double requiredDouble(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IOException("Missing numeric field: " + field);
        }
        return value.asDouble();
    }

    private static long requiredLong(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IOException("Missing long field: " + field);
        }
        return value.asLong();
    }
}
