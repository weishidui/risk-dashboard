package com.riskcontrol.offline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class ProvinceResolver {
    private final Map<String, String> cityToProvince = new HashMap<String, String>();

    ProvinceResolver() {
        InputStream input = ProvinceResolver.class.getClassLoader().getResourceAsStream("city_province.csv");
        if (input == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length < 2) {
                    continue;
                }
                String province = TextUtil.clean(parts[0]);
                String city = TextUtil.clean(parts[1]);
                if (!province.isEmpty() && !city.isEmpty()) {
                    cityToProvince.put(city, province);
                    cityToProvince.put(stripCitySuffix(city), province);
                }
            }
        } catch (IOException ignored) {
            cityToProvince.clear();
        }
    }

    String resolve(String city) {
        String clean = TextUtil.clean(city);
        String province = cityToProvince.get(clean);
        if (province == null) {
            province = cityToProvince.get(stripCitySuffix(clean));
        }
        return province == null || province.isEmpty() ? "未知" : province;
    }

    private static String stripCitySuffix(String city) {
        return city.endsWith("市") ? city.substring(0, city.length() - 1) : city;
    }
}
