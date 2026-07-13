package com.riskcontrol.offline;

import org.apache.hadoop.conf.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

final class JdbcConfig {
    final boolean enabled;
    final String url;
    final String user;
    final String password;

    private JdbcConfig(boolean enabled, String url, String user, String password) {
        this.enabled = enabled;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    static JdbcConfig from(Configuration conf, String enabledKey) {
        return new JdbcConfig(
                conf.getBoolean(enabledKey, true),
                conf.get("offline.jdbc.url",
                        "jdbc:mysql://192.168.154.104:3306/risk_control?useUnicode=true&characterEncoding=utf8&useSSL=false"),
                conf.get("offline.jdbc.user", "root"),
                conf.get("offline.jdbc.password", "123456"));
    }

    Connection open() throws ClassNotFoundException, SQLException {
        if (!enabled) {
            return null;
        }
        Class.forName("com.mysql.jdbc.Driver");
        return DriverManager.getConnection(url, user, password);
    }
}
