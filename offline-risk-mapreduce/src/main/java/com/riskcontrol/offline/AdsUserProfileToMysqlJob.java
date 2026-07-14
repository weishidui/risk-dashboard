package com.riskcontrol.offline;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.exceptions.JedisException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AdsUserProfileToMysqlJob extends Configured implements Tool {
    private static final String UPSERT_SQL = "INSERT INTO user_profile "
            + "(user_id,avg_amt_30d,common_cities,common_devs,common_pay_channels,common_trans_types,common_counterparties,"
            + "last_trans_ts,last_city,last_ip,last_login_time,registration_time,total_balance,single_limit,daily_limit,monthly_limit,"
            + "account_status,login_count_24h,trans_count_24h,trans_amount_24h,trans_count_7d,cancel_retry_count,risk_tags,risk_score) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE "
            + "avg_amt_30d=VALUES(avg_amt_30d),common_cities=VALUES(common_cities),common_devs=VALUES(common_devs),"
            + "common_pay_channels=VALUES(common_pay_channels),common_trans_types=VALUES(common_trans_types),"
            + "common_counterparties=VALUES(common_counterparties),last_trans_ts=VALUES(last_trans_ts),"
            + "last_city=VALUES(last_city),last_ip=VALUES(last_ip),last_login_time=VALUES(last_login_time),"
            + "registration_time=VALUES(registration_time),total_balance=VALUES(total_balance),"
            + "single_limit=VALUES(single_limit),daily_limit=VALUES(daily_limit),monthly_limit=VALUES(monthly_limit),"
            + "account_status=VALUES(account_status),login_count_24h=VALUES(login_count_24h),"
            + "trans_count_24h=VALUES(trans_count_24h),trans_amount_24h=VALUES(trans_amount_24h),"
            + "trans_count_7d=VALUES(trans_count_7d),cancel_retry_count=VALUES(cancel_retry_count),"
            + "risk_tags=VALUES(risk_tags),risk_score=VALUES(risk_score),update_time=CURRENT_TIMESTAMP";

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: ads-user-profile-to-mysql <ads_user_profile_hdfs_path>");
            return 2;
        }
        Path input = new Path(args[0]);
        FileSystem fs = input.getFileSystem(getConf());
        if (!fs.exists(input)) {
            throw new IOException("ADS profile path does not exist: " + input);
        }

        JdbcConfig jdbcConfig = JdbcConfig.from(getConf(), "offline.profile.mysql.enabled");
        if (!jdbcConfig.enabled) {
            System.out.println("MySQL import disabled by offline.profile.mysql.enabled=false");
            return 0;
        }

        int batchSize = getConf().getInt("offline.profile.mysql.batch.size", 500);
        ImportCounters counters = new ImportCounters();
        Jedis jedis = null;
        Pipeline pipeline = null;
        try (Connection connection = jdbcConfig.open();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            connection.setAutoCommit(false);
            try {
                jedis = RedisConfig.from(getConf()).open();
                if (jedis != null) {
                    pipeline = jedis.pipelined();
                }
            } catch (JedisException e) {
                pipeline = null;
                jedis = null;
                counters.redisDisabled++;
            }

            importPath(fs, input, statement, connection, pipeline, batchSize, counters);
            flush(statement, connection, counters);
            if (pipeline != null) {
                try {
                    pipeline.sync();
                } catch (JedisException e) {
                    counters.redisFailed++;
                }
            }
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }

        System.out.println("ADS user_profile import completed.");
        System.out.println("Input rows: " + counters.inputRows);
        System.out.println("Imported MySQL rows: " + counters.mysqlRows);
        System.out.println("Bad rows: " + counters.badRows);
        System.out.println("Redis rows: " + counters.redisRows);
        System.out.println("Redis disabled count: " + counters.redisDisabled);
        System.out.println("Redis failed count: " + counters.redisFailed);
        return counters.badRows == counters.inputRows && counters.inputRows > 0 ? 1 : 0;
    }

    private void importPath(FileSystem fs, Path path, PreparedStatement statement, Connection connection,
                            Pipeline pipeline, int batchSize, ImportCounters counters)
            throws IOException, SQLException {
        for (FileStatus status : fs.listStatus(path)) {
            Path child = status.getPath();
            String name = child.getName();
            if (status.isDirectory()) {
                if (!name.startsWith("_") && !name.startsWith(".")) {
                    importPath(fs, child, statement, connection, pipeline, batchSize, counters);
                }
            } else if (status.isFile() && name.startsWith("part-")) {
                importPartFile(fs, child, statement, connection, pipeline, batchSize, counters);
            }
        }
    }

    private void importPartFile(FileSystem fs, Path path, PreparedStatement statement, Connection connection,
                                Pipeline pipeline, int batchSize, ImportCounters counters)
            throws IOException, SQLException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(fs.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                counters.inputRows++;
                ProfileRow row = ProfileRow.parse(line);
                if (row == null) {
                    counters.badRows++;
                    continue;
                }
                bind(statement, row);
                statement.addBatch();
                counters.pendingMysqlRows++;
                counters.mysqlRows++;
                writeRedis(pipeline, row, counters);
                if (counters.pendingMysqlRows >= batchSize) {
                    flush(statement, connection, counters);
                }
            }
        }
    }

    private static void flush(PreparedStatement statement, Connection connection, ImportCounters counters)
            throws SQLException {
        if (counters.pendingMysqlRows == 0) {
            return;
        }
        statement.executeBatch();
        connection.commit();
        counters.pendingMysqlRows = 0;
    }

    private static void bind(PreparedStatement statement, ProfileRow row) throws SQLException {
        int i = 1;
        statement.setString(i++, truncate(row.userId, 64));
        statement.setDouble(i++, row.avgAmt30d);
        statement.setString(i++, truncate(row.commonCities, 255));
        statement.setString(i++, truncate(row.commonDevs, 255));
        statement.setString(i++, truncate(row.commonPayChannels, 255));
        statement.setString(i++, truncate(row.commonTransTypes, 255));
        statement.setString(i++, truncate(row.commonCounterparties, 255));
        statement.setLong(i++, row.lastTransTs);
        statement.setString(i++, truncate(row.lastCity, 64));
        statement.setString(i++, truncate(row.lastIp, 64));
        statement.setLong(i++, row.lastLoginTime);
        statement.setLong(i++, row.registrationTime);
        statement.setDouble(i++, row.totalBalance);
        statement.setDouble(i++, row.singleLimit);
        statement.setDouble(i++, row.dailyLimit);
        statement.setDouble(i++, row.monthlyLimit);
        statement.setString(i++, truncate(valueOrDefault(row.accountStatus, "normal"), 20));
        statement.setInt(i++, row.loginCount24h);
        statement.setInt(i++, row.transCount24h);
        statement.setDouble(i++, row.transAmount24h);
        statement.setInt(i++, row.transCount7d);
        statement.setInt(i++, row.cancelRetryCount);
        statement.setString(i++, truncate(row.riskTags, 255));
        statement.setInt(i, row.riskScore);
    }

    private static void writeRedis(Pipeline pipeline, ProfileRow row, ImportCounters counters) {
        if (pipeline == null) {
            return;
        }
        try {
            pipeline.hmset("profile:" + row.userId, row.toRedisHash());
            counters.redisRows++;
            if (counters.redisRows % 1000 == 0) {
                pipeline.sync();
            }
        } catch (JedisException e) {
            counters.redisFailed++;
        }
    }

    private static String truncate(String value, int maxLength) {
        String safe = value == null ? "" : TextUtil.clean(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static final class ImportCounters {
        int inputRows;
        int mysqlRows;
        int pendingMysqlRows;
        int badRows;
        int redisRows;
        int redisDisabled;
        int redisFailed;
    }

    private static final class ProfileRow {
        String userId;
        double avgAmt30d;
        String commonCities;
        String commonDevs;
        String commonPayChannels;
        String commonTransTypes;
        String commonCounterparties;
        long lastTransTs;
        String lastCity;
        String lastIp;
        long lastLoginTime;
        long registrationTime;
        double totalBalance;
        double singleLimit;
        double dailyLimit;
        double monthlyLimit;
        String accountStatus;
        int loginCount24h;
        int transCount24h;
        double transAmount24h;
        int transCount7d;
        int cancelRetryCount;
        String riskTags;
        int riskScore;

        static ProfileRow parse(String line) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 24) {
                return null;
            }
            try {
                ProfileRow row = new ProfileRow();
                row.userId = parts[0];
                row.avgAmt30d = parseDouble(parts[1]);
                row.commonCities = parts[2];
                row.commonDevs = parts[3];
                row.commonPayChannels = parts[4];
                row.commonTransTypes = parts[5];
                row.commonCounterparties = parts[6];
                row.lastTransTs = parseLong(parts[7]);
                row.lastCity = parts[8];
                row.lastIp = parts[9];
                row.lastLoginTime = parseLong(parts[10]);
                row.registrationTime = parseLong(parts[11]);
                row.totalBalance = parseDouble(parts[12]);
                row.singleLimit = parseDouble(parts[13]);
                row.dailyLimit = parseDouble(parts[14]);
                row.monthlyLimit = parseDouble(parts[15]);
                row.accountStatus = parts[16];
                row.loginCount24h = parseInt(parts[17]);
                row.transCount24h = parseInt(parts[18]);
                row.transAmount24h = parseDouble(parts[19]);
                row.transCount7d = parseInt(parts[20]);
                row.cancelRetryCount = parseInt(parts[21]);
                row.riskTags = parts[22];
                row.riskScore = parseInt(parts[23]);
                return row;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        Map<String, String> toRedisHash() {
            Map<String, String> hash = new HashMap<String, String>();
            hash.put("avg_amt_30d", String.format(Locale.US, "%.2f", avgAmt30d));
            hash.put("common_cities", commonCities);
            hash.put("common_devs", commonDevs);
            hash.put("common_pay_channels", commonPayChannels);
            hash.put("common_trans_types", commonTransTypes);
            hash.put("common_counterparties", commonCounterparties);
            hash.put("last_trans_ts", String.valueOf(lastTransTs));
            hash.put("last_city", lastCity);
            hash.put("last_ip", lastIp);
            hash.put("last_login_time", String.valueOf(lastLoginTime));
            hash.put("registration_time", String.valueOf(registrationTime));
            hash.put("total_balance", String.format(Locale.US, "%.2f", totalBalance));
            hash.put("single_limit", String.format(Locale.US, "%.2f", singleLimit));
            hash.put("daily_limit", String.format(Locale.US, "%.2f", dailyLimit));
            hash.put("monthly_limit", String.format(Locale.US, "%.2f", monthlyLimit));
            hash.put("account_status", valueOrDefault(accountStatus, "normal"));
            hash.put("login_count_24h", String.valueOf(loginCount24h));
            hash.put("trans_count_24h", String.valueOf(transCount24h));
            hash.put("trans_amount_24h", String.format(Locale.US, "%.2f", transAmount24h));
            hash.put("trans_count_7d", String.valueOf(transCount7d));
            hash.put("cancel_retry_count", String.valueOf(cancelRetryCount));
            hash.put("risk_tags", riskTags);
            hash.put("risk_score", String.valueOf(riskScore));
            return hash;
        }

        private static long parseLong(String value) {
            return value == null || value.isEmpty() ? 0L : Long.parseLong(value);
        }

        private static int parseInt(String value) {
            return value == null || value.isEmpty() ? 0 : (int) Double.parseDouble(value);
        }

        private static double parseDouble(String value) {
            return value == null || value.isEmpty() ? 0D : Double.parseDouble(value);
        }
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new AdsUserProfileToMysqlJob(), args));
    }
}
