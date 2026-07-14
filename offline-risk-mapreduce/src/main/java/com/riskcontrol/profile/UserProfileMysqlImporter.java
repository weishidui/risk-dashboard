package com.riskcontrol.profile;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

final class UserProfileMysqlImporter {
    private UserProfileMysqlImporter() {
    }

    static int importOutput(Configuration conf, Path outputPath) throws IOException, SQLException, ClassNotFoundException {
        String jdbcUrl = conf.get("profile.jdbc.url");
        String jdbcUser = conf.get("profile.jdbc.user", "root");
        String jdbcPassword = conf.get("profile.jdbc.password", "123456");
        RedisConfig redisConfig = RedisConfig.from(conf);
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IOException("Missing -D profile.jdbc.url");
        }

        Class.forName("com.mysql.jdbc.Driver");
        FileSystem fs = outputPath.getFileSystem(conf);
        FileStatus[] statuses = fs.listStatus(outputPath);
        int imported = 0;
        int syncedToRedis = 0;
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
             Jedis jedis = redisConfig.open()) {
            connection.setAutoCommit(false);
            Pipeline pipeline = jedis == null ? null : jedis.pipelined();
            String sql = "INSERT INTO user_profile "
                    + "(user_id, avg_amt_30d, common_cities, common_devs, last_trans_ts, last_city) "
                    + "VALUES (?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "avg_amt_30d=VALUES(avg_amt_30d), "
                    + "common_cities=VALUES(common_cities), "
                    + "common_devs=VALUES(common_devs), "
                    + "last_trans_ts=VALUES(last_trans_ts), "
                    + "last_city=VALUES(last_city)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (FileStatus status : statuses) {
                    if (!status.isFile() || !status.getPath().getName().startsWith("part-")) {
                        continue;
                    }
                    ImportCounters counters = importPartFile(fs, status.getPath(), statement, pipeline, redisConfig);
                    imported += counters.mysqlRows;
                    syncedToRedis += counters.redisRows;
                }
                statement.executeBatch();
                if (pipeline != null) {
                    pipeline.sync();
                }
            }
            connection.commit();
        }
        if (redisConfig.enabled) {
            System.out.println("Synced Redis profile keys: " + syncedToRedis);
        }
        return imported;
    }

    private static ImportCounters importPartFile(FileSystem fs, Path path, PreparedStatement statement,
                                                Pipeline pipeline, RedisConfig redisConfig)
            throws IOException, SQLException {
        ImportCounters counters = new ImportCounters();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(fs.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                UserProfileRow row = UserProfileRow.parse(line);
                if (row == null) {
                    continue;
                }
                bindMysql(statement, row);
                statement.addBatch();
                counters.mysqlRows++;
                if (pipeline != null) {
                    writeRedis(pipeline, redisConfig, row);
                    counters.redisRows++;
                }
                if (counters.mysqlRows % 500 == 0) {
                    statement.executeBatch();
                    if (pipeline != null) {
                        pipeline.sync();
                    }
                }
            }
        }
        return counters;
    }

    private static void bindMysql(PreparedStatement statement, UserProfileRow row) throws SQLException {
        statement.setString(1, row.userId);
        statement.setDouble(2, row.avgAmt30d);
        statement.setString(3, row.commonCities);
        statement.setString(4, row.commonDevs);
        statement.setLong(5, row.lastTransTs);
        statement.setString(6, row.lastCity);
    }

    private static void writeRedis(Pipeline pipeline, RedisConfig redisConfig, UserProfileRow row) {
        String key = redisConfig.keyPrefix + row.userId;
        Map<String, String> values = new HashMap<String, String>();
        values.put("avg_amt_30d", String.format(java.util.Locale.US, "%.2f", row.avgAmt30d));
        values.put("common_cities", row.commonCities);
        values.put("common_devs", row.commonDevs);
        values.put("last_trans_ts", String.valueOf(row.lastTransTs));
        values.put("last_city", row.lastCity);
        pipeline.hmset(key, values);
        if (redisConfig.ttlSeconds > 0) {
            pipeline.expire(key, redisConfig.ttlSeconds);
        }
    }

    private static final class ImportCounters {
        int mysqlRows;
        int redisRows;
    }

    private static final class UserProfileRow {
        final String userId;
        final double avgAmt30d;
        final String commonCities;
        final String commonDevs;
        final long lastTransTs;
        final String lastCity;

        private UserProfileRow(String userId, double avgAmt30d, String commonCities,
                               String commonDevs, long lastTransTs, String lastCity) {
            this.userId = userId;
            this.avgAmt30d = avgAmt30d;
            this.commonCities = commonCities;
            this.commonDevs = commonDevs;
            this.lastTransTs = lastTransTs;
            this.lastCity = lastCity;
        }

        static UserProfileRow parse(String line) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 6) {
                return null;
            }
            try {
                return new UserProfileRow(
                        parts[0],
                        Double.parseDouble(parts[1]),
                        parts[2],
                        parts[3],
                        Long.parseLong(parts[4]),
                        parts[5]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static final class RedisConfig {
        final boolean enabled;
        final String host;
        final int port;
        final String password;
        final String keyPrefix;
        final int ttlSeconds;

        private RedisConfig(boolean enabled, String host, int port, String password, String keyPrefix, int ttlSeconds) {
            this.enabled = enabled;
            this.host = host;
            this.port = port;
            this.password = password;
            this.keyPrefix = keyPrefix;
            this.ttlSeconds = ttlSeconds;
        }

        static RedisConfig from(Configuration conf) {
            return new RedisConfig(
                    conf.getBoolean("profile.redis.enabled", true),
                    conf.get("profile.redis.host", "192.168.154.104"),
                    conf.getInt("profile.redis.port", 6379),
                    conf.get("profile.redis.password", "123456"),
                    conf.get("profile.redis.key.prefix", "profile:"),
                    conf.getInt("profile.redis.ttl.seconds", 0));
        }

        Jedis open() {
            if (!enabled) {
                return null;
            }
            Jedis jedis = new Jedis(host, port);
            if (password != null && !password.trim().isEmpty()) {
                jedis.auth(password);
            }
            return jedis;
        }
    }
}
