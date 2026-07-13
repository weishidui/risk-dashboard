package com.riskcontrol.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class CleanTransRecord {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    final String transId;
    final String userId;
    final double amount;
    final long timestamp;
    final String city;
    final String geoLocation;
    final String deviceId;
    final String networkType;
    final int devScore;
    final String ipAddress;
    final String osType;
    final String osVersion;
    final String screenResolution;
    final int batteryLevel;
    final boolean rootJailbreak;
    final String simOperator;
    final String userAgent;
    final String dnsServer;
    final String wifiSsid;
    final String transType;
    final String payChannel;
    final String inputMethod;
    final long clickDuration;
    final String note;
    final String pageUrl;
    final String counterpartyId;
    final String counterpartyName;
    final String counterpartyBank;
    final String loginSessionId;
    final int loginFailCount;
    final int cancelRetryCount;
    final long processTime;
    final String dt;

    private CleanTransRecord(ObjectNode node) {
        this.transId = text(node, "trans_id");
        this.userId = text(node, "user_id");
        this.amount = decimal2(number(node, "amount", 0D));
        this.timestamp = longNumber(node, "timestamp", longNumber(node, "trans_timestamp", 0L));
        this.city = normalizeCity(text(node, "city"));
        this.geoLocation = text(node, "geo_location");
        this.deviceId = text(node, "device_id");
        this.networkType = normalizeNetwork(text(node, "network_type"));
        this.devScore = intNumber(node, "dev_score", 0);
        this.ipAddress = text(node, "ip_address");
        this.osType = text(node, "os_type");
        this.osVersion = text(node, "os_version");
        this.screenResolution = text(node, "screen_resolution");
        this.batteryLevel = intNumber(node, "battery_level", -1);
        this.rootJailbreak = bool(node, "root_jailbreak", false);
        this.simOperator = defaultText(text(node, "sim_operator"), "unknown");
        this.userAgent = text(node, "user_agent");
        this.dnsServer = text(node, "dns_server");
        this.wifiSsid = text(node, "wifi_ssid");
        this.transType = text(node, "trans_type");
        this.payChannel = normalizePayChannel(text(node, "pay_channel"));
        this.inputMethod = text(node, "input_method");
        this.clickDuration = longNumber(node, "click_duration", 0L);
        this.note = text(node, "note");
        this.pageUrl = text(node, "page_url");
        this.counterpartyId = text(node, "counterparty_id");
        this.counterpartyName = text(node, "counterparty_name");
        this.counterpartyBank = text(node, "counterparty_bank");
        this.loginSessionId = text(node, "login_session_id");
        this.loginFailCount = intNumber(node, "login_fail_count", 0);
        this.cancelRetryCount = intNumber(node, "cancel_retry_count", 0);
        this.processTime = longNumber(node, "process_time", System.currentTimeMillis());
        this.dt = defaultText(text(node, "dt"), DateUtil.dtFromMillis(this.timestamp));
    }

    static CleanTransRecord parse(String json) throws IOException {
        JsonNode raw = MAPPER.readTree(json);
        if (!raw.isObject()) {
            throw new IOException("JSON line is not an object");
        }
        CleanTransRecord record = new CleanTransRecord((ObjectNode) raw);
        record.validate();
        return record;
    }

    String toJson() throws IOException {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("trans_id", transId);
        node.put("user_id", userId);
        node.put("amount", amount);
        node.put("timestamp", timestamp);
        node.put("city", city);
        node.put("geo_location", geoLocation);
        node.put("device_id", deviceId);
        node.put("network_type", networkType);
        node.put("dev_score", devScore);
        node.put("ip_address", ipAddress);
        node.put("os_type", osType);
        node.put("os_version", osVersion);
        node.put("screen_resolution", screenResolution);
        node.put("battery_level", batteryLevel);
        node.put("root_jailbreak", rootJailbreak);
        node.put("sim_operator", simOperator);
        node.put("user_agent", userAgent);
        node.put("dns_server", dnsServer);
        node.put("wifi_ssid", wifiSsid);
        node.put("trans_type", transType);
        node.put("pay_channel", payChannel);
        node.put("input_method", inputMethod);
        node.put("click_duration", clickDuration);
        node.put("note", note);
        node.put("page_url", pageUrl);
        node.put("counterparty_id", counterpartyId);
        node.put("counterparty_name", counterpartyName);
        node.put("counterparty_bank", counterpartyBank);
        node.put("login_session_id", loginSessionId);
        node.put("login_fail_count", loginFailCount);
        node.put("cancel_retry_count", cancelRetryCount);
        node.put("process_time", processTime);
        node.put("dt", dt);
        return MAPPER.writeValueAsString(node);
    }

    private void validate() throws IOException {
        if (transId.isEmpty()) {
            throw new IOException("missing trans_id");
        }
        if (userId.isEmpty()) {
            throw new IOException("missing user_id");
        }
        if (amount <= 0D) {
            throw new IOException("invalid amount");
        }
        if (timestamp <= 0L) {
            throw new IOException("invalid timestamp");
        }
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return TextUtil.clean(value.asText());
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static double number(ObjectNode node, String field, double fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : fallback;
    }

    private static long longNumber(ObjectNode node, String field, long fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asLong() : fallback;
    }

    private static int intNumber(ObjectNode node, String field, int fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt() : fallback;
    }

    private static boolean bool(ObjectNode node, String field, boolean fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        String text = value.asText();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private static double decimal2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String normalizeCity(String city) {
        if (city == null || city.isEmpty()) {
            return "未知";
        }
        if ("北京市".equals(city)) {
            return "北京";
        }
        if ("上海市".equals(city)) {
            return "上海";
        }
        if ("广州市".equals(city)) {
            return "广州";
        }
        if ("深圳市".equals(city)) {
            return "深圳";
        }
        return city;
    }

    private static String normalizePayChannel(String channel) {
        if ("银行卡".equals(channel)) {
            return "bank_card";
        }
        if ("微信".equals(channel)) {
            return "wechat";
        }
        if ("支付宝".equals(channel)) {
            return "alipay";
        }
        if ("余额".equals(channel)) {
            return "balance";
        }
        return channel == null || channel.isEmpty() ? "unknown" : channel;
    }

    private static String normalizeNetwork(String networkType) {
        if ("WIFI".equalsIgnoreCase(networkType) || "Wi-Fi".equalsIgnoreCase(networkType)) {
            return "WiFi";
        }
        if ("4g".equalsIgnoreCase(networkType)) {
            return "4G";
        }
        if ("5g".equalsIgnoreCase(networkType)) {
            return "5G";
        }
        return networkType == null || networkType.isEmpty() ? "unknown" : networkType;
    }
}
