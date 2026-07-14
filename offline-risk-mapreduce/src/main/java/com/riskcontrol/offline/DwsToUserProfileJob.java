package com.riskcontrol.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DwsToUserProfileJob extends Configured implements Tool {
    public static class ProfileMapper extends Mapper<Object, Text, Text, Text> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                JsonNode node = MAPPER.readTree(value.toString());
                String userId = text(node, "user_id");
                if (userId.isEmpty()) {
                    return;
                }
                outKey.set(userId);
                if (node.has("avg_amount") && node.has("distinct_cities")) {
                    outValue.set("D\t" + number(node, "avg_amount") + "\t"
                            + text(node, "distinct_cities") + "\t"
                            + text(node, "distinct_devices") + "\t"
                            + text(node, "pay_channels_used") + "\t"
                            + text(node, "trans_types_used") + "\t"
                            + text(node, "distinct_counterparties") + "\t"
                            + number(node, "trans_count") + "\t"
                            + number(node, "total_amount") + "\t"
                            + longNumber(node, "last_trans_ts") + "\t"
                            + longNumber(node, "cancel_retry_count") + "\t"
                            + text(node, "dt"));
                } else if (node.has("registration_time") && node.has("single_limit")) {
                    outValue.set("B\t" + longNumber(node, "registration_time") + "\t"
                            + number(node, "total_balance") + "\t"
                            + number(node, "single_limit") + "\t"
                            + number(node, "daily_limit") + "\t"
                            + number(node, "monthly_limit") + "\t"
                            + text(node, "account_status") + "\t"
                            + text(node, "risk_tags"));
                } else if (node.has("login_time") && node.has("ip_address")) {
                    outValue.set("L\t" + longNumber(node, "login_time") + "\t" + text(node, "ip_address"));
                } else if (node.has("risk_score") && node.has("risk_tags")) {
                    outValue.set("R\t" + number(node, "risk_score") + "\t" + text(node, "risk_tags"));
                } else {
                    return;
                }
                context.write(outKey, outValue);
            } catch (Exception e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "dws_profile_bad_rows").increment(1L);
            }
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || value.isNull() ? "" : TextUtil.clean(value.asText());
        }

        private static double number(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || !value.isNumber() ? 0D : value.asDouble();
        }

        private static long longNumber(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || !value.isNumber() ? 0L : value.asLong();
        }
    }

    public static class ProfileReducer extends Reducer<Text, Text, NullWritable, Text> {
        private final Text outValue = new Text();
        private Connection connection;
        private PreparedStatement upsertProfile;
        private Jedis jedis;
        private Pipeline redisPipeline;
        private int pendingDbWrites;
        private int pendingRedisWrites;
        private int dbBatchSize;

        @Override
        protected void setup(Context context) throws IOException {
            try {
                JdbcConfig jdbcConfig = JdbcConfig.from(context.getConfiguration(), "offline.profile.mysql.enabled");
                connection = jdbcConfig.open();
                if (connection != null) {
                    connection.setAutoCommit(false);
                    upsertProfile = connection.prepareStatement(
                            "INSERT INTO user_profile "
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
                                    + "risk_tags=VALUES(risk_tags),risk_score=VALUES(risk_score),update_time=CURRENT_TIMESTAMP");
                }
                dbBatchSize = context.getConfiguration().getInt("offline.profile.mysql.batch.size", 1000);
            } catch (Exception e) {
                throw new IOException("Failed to initialize profile sinks", e);
            }
            try {
                jedis = RedisConfig.from(context.getConfiguration()).open();
                if (jedis != null) {
                    redisPipeline = jedis.pipelined();
                }
            } catch (JedisException e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "profile_redis_disabled").increment(1L);
                jedis = null;
                redisPipeline = null;
            }
        }

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            int dayCount = 0;
            double avgSum = 0D;
            int transCount7d = 0;
            int transCount24h = 0;
            double transAmount24h = 0D;
            long latestTs = 0L;
            String latestCity = "";
            Map<String, Integer> cities = new HashMap<String, Integer>();
            Map<String, Integer> devices = new HashMap<String, Integer>();
            Map<String, Integer> payChannels = new HashMap<String, Integer>();
            Map<String, Integer> transTypes = new HashMap<String, Integer>();
            Map<String, Integer> counterparties = new HashMap<String, Integer>();
            String latestDt = "";
            int cancelRetryCount = 0;
            ProfileRow dim = new ProfileRow();
            List<LoginRow> logins = new ArrayList<LoginRow>();
            int historyRiskScore = 0;
            Map<String, Integer> riskTags = new HashMap<String, Integer>();

            for (Text value : values) {
                String[] p = value.toString().split("\t", -1);
                if (p.length == 0) {
                    continue;
                }
                try {
                    if ("D".equals(p[0]) && p.length >= 12) {
                        double avgAmount = Double.parseDouble(p[1]);
                        int transCount = (int) Double.parseDouble(p[7]);
                        double totalAmount = Double.parseDouble(p[8]);
                        long ts = Long.parseLong(p[9]);
                        int retries = (int) Double.parseDouble(p[10]);
                        String dt = p[11];
                        avgSum += avgAmount;
                        dayCount++;
                        transCount7d += transCount;
                        cancelRetryCount += Math.max(0, retries);
                        if (dt.compareTo(latestDt) >= 0) {
                            latestDt = dt;
                            transCount24h = transCount;
                            transAmount24h = totalAmount;
                        }
                        mergeCsv(cities, p[2]);
                        mergeCsv(devices, p[3]);
                        mergeCsv(payChannels, p[4]);
                        mergeCsv(transTypes, p[5]);
                        mergeCsv(counterparties, p[6]);
                        if (ts > latestTs) {
                            latestTs = ts;
                            latestCity = firstCsv(p[2]);
                        }
                    } else if ("B".equals(p[0]) && p.length >= 8) {
                        dim.registrationTime = Long.parseLong(p[1]);
                        dim.totalBalance = Double.parseDouble(p[2]);
                        dim.singleLimit = Double.parseDouble(p[3]);
                        dim.dailyLimit = Double.parseDouble(p[4]);
                        dim.monthlyLimit = Double.parseDouble(p[5]);
                        dim.accountStatus = valueOrDefault(p[6], "normal");
                        mergeCsv(riskTags, p[7]);
                    } else if ("L".equals(p[0]) && p.length >= 3) {
                        LoginRow login = new LoginRow();
                        login.loginTime = Long.parseLong(p[1]);
                        login.ipAddress = p[2];
                        logins.add(login);
                    } else if ("R".equals(p[0]) && p.length >= 3) {
                        historyRiskScore += (int) Double.parseDouble(p[1]);
                        mergeCsv(riskTags, p[2]);
                    }
                } catch (NumberFormatException e) {
                    context.getCounter(OfflineConstants.COUNTER_GROUP, "dws_profile_bad_numbers").increment(1L);
                }
            }
            if (dayCount == 0) {
                return;
            }

            ProfileRow row = new ProfileRow();
            row.userId = key.toString();
            row.avgAmt30d = avgSum / dayCount;
            row.commonCities = TextUtil.topN(cities, 5);
            row.commonDevs = TextUtil.topN(devices, 5);
            row.commonPayChannels = TextUtil.topN(payChannels, 3);
            row.commonTransTypes = TextUtil.topN(transTypes, 3);
            row.commonCounterparties = TextUtil.topN(counterparties, 5);
            row.lastTransTs = latestTs;
            row.lastCity = latestCity;
            row.transCount24h = transCount24h;
            row.transAmount24h = transAmount24h;
            row.transCount7d = transCount7d;
            row.cancelRetryCount = cancelRetryCount;
            row.registrationTime = dim.registrationTime;
            row.totalBalance = dim.totalBalance;
            row.singleLimit = dim.singleLimit;
            row.dailyLimit = dim.dailyLimit;
            row.monthlyLimit = dim.monthlyLimit;
            row.accountStatus = dim.accountStatus;
            applyLoginProfile(row, logins);
            row.riskScore = historyRiskScore;
            row.riskTags = TextUtil.topN(riskTags, 20);

            outValue.set(row.toTsv());
            context.write(NullWritable.get(), outValue);
            writeSinks(row, context);
        }

        @Override
        protected void cleanup(Context context) throws IOException {
            try {
                if (upsertProfile != null) {
                    flushProfileBatch();
                }
                if (redisPipeline != null) {
                    try {
                        redisPipeline.sync();
                    } catch (JedisException e) {
                        context.getCounter(OfflineConstants.COUNTER_GROUP, "profile_redis_flush_failed").increment(1L);
                    }
                }
                if (connection != null) {
                    connection.commit();
                    connection.close();
                }
                if (jedis != null) {
                    jedis.close();
                }
            } catch (SQLException e) {
                throw new IOException("Failed to close profile sinks", e);
            }
        }

        private void writeSinks(ProfileRow row, Context context) throws IOException {
            try {
                if (upsertProfile != null) {
                    bindProfile(row);
                    upsertProfile.addBatch();
                    pendingDbWrites++;
                    if (pendingDbWrites >= dbBatchSize) {
                        flushProfileBatch();
                    }
                }
                if (redisPipeline != null) {
                    try {
                        Map<String, String> hash = row.toRedisHash();
                        redisPipeline.hmset("profile:" + row.userId, hash);
                        pendingRedisWrites++;
                        if (pendingRedisWrites >= 1000) {
                            redisPipeline.sync();
                            pendingRedisWrites = 0;
                        }
                    } catch (JedisException e) {
                        context.getCounter(OfflineConstants.COUNTER_GROUP, "profile_redis_write_failed").increment(1L);
                        try {
                            redisPipeline.sync();
                        } catch (JedisException ignored) {
                            // Redis is an acceleration sink; keep HDFS/MySQL output successful.
                        }
                        redisPipeline = null;
                    }
                }
                context.getCounter(OfflineConstants.COUNTER_GROUP, "profile_rows").increment(1L);
            } catch (SQLException e) {
                throw new IOException("Failed to write profile row", e);
            }
        }

        private void flushProfileBatch() throws SQLException {
            if (upsertProfile == null || pendingDbWrites == 0) {
                return;
            }
            upsertProfile.executeBatch();
            connection.commit();
            pendingDbWrites = 0;
        }

        private static String valueOrDefault(String value, String fallback) {
            return value == null || value.trim().isEmpty() ? fallback : TextUtil.clean(value);
        }

        private static void applyLoginProfile(ProfileRow row, List<LoginRow> logins) {
            long windowEnd = row.lastTransTs;
            long windowStart = Math.max(0L, windowEnd - 24L * 60L * 60L * 1000L);
            for (LoginRow login : logins) {
                if (login.loginTime > row.lastLoginTime) {
                    row.lastLoginTime = login.loginTime;
                    row.lastIp = login.ipAddress;
                }
                if (login.loginTime >= windowStart && login.loginTime <= windowEnd) {
                    row.loginCount24h++;
                }
            }
        }

        private void bindProfile(ProfileRow row) throws SQLException {
            int i = 1;
            upsertProfile.setString(i++, row.userId);
            upsertProfile.setDouble(i++, row.avgAmt30d);
            upsertProfile.setString(i++, row.commonCities);
            upsertProfile.setString(i++, row.commonDevs);
            upsertProfile.setString(i++, row.commonPayChannels);
            upsertProfile.setString(i++, row.commonTransTypes);
            upsertProfile.setString(i++, row.commonCounterparties);
            upsertProfile.setLong(i++, row.lastTransTs);
            upsertProfile.setString(i++, row.lastCity);
            upsertProfile.setString(i++, row.lastIp);
            upsertProfile.setLong(i++, row.lastLoginTime);
            upsertProfile.setLong(i++, row.registrationTime);
            upsertProfile.setDouble(i++, row.totalBalance);
            upsertProfile.setDouble(i++, row.singleLimit);
            upsertProfile.setDouble(i++, row.dailyLimit);
            upsertProfile.setDouble(i++, row.monthlyLimit);
            upsertProfile.setString(i++, row.accountStatus);
            upsertProfile.setInt(i++, row.loginCount24h);
            upsertProfile.setInt(i++, row.transCount24h);
            upsertProfile.setDouble(i++, row.transAmount24h);
            upsertProfile.setInt(i++, row.transCount7d);
            upsertProfile.setInt(i++, row.cancelRetryCount);
            upsertProfile.setString(i++, row.riskTags);
            upsertProfile.setInt(i, row.riskScore);
        }

        private static void mergeCsv(Map<String, Integer> counts, String csv) {
            String[] values = TextUtil.splitCsv(csv);
            for (String value : values) {
                TextUtil.increment(counts, value);
            }
        }

        private static String firstCsv(String csv) {
            String[] values = TextUtil.splitCsv(csv);
            return values.length == 0 ? "" : values[0];
        }
    }

    private static final class LoginRow {
        long loginTime;
        String ipAddress;
    }

    private static final class ProfileRow {
        String userId;
        double avgAmt30d;
        String commonCities;
        String commonDevs;
        String commonPayChannels;
        String commonTransTypes = "";
        String commonCounterparties;
        long lastTransTs;
        String lastCity;
        String lastIp = "";
        long lastLoginTime = 0L;
        long registrationTime = 0L;
        double totalBalance = 100000D;
        double singleLimit = 50000D;
        double dailyLimit = 200000D;
        double monthlyLimit = 1000000D;
        String accountStatus = "normal";
        int loginCount24h = 0;
        int transCount24h;
        double transAmount24h;
        int transCount7d;
        int cancelRetryCount = 0;
        String riskTags = "";
        int riskScore = 0;

        String toTsv() {
            return String.format(Locale.US,
                    "%s\t%.2f\t%s\t%s\t%s\t%s\t%s\t%d\t%s\t%s\t%d\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%s\t%d\t%d\t%.2f\t%d\t%d\t%s\t%d",
                    userId, avgAmt30d, commonCities, commonDevs, commonPayChannels, commonTransTypes,
                    commonCounterparties, lastTransTs, lastCity, lastIp, lastLoginTime, registrationTime,
                    totalBalance, singleLimit, dailyLimit, monthlyLimit, accountStatus, loginCount24h,
                    transCount24h, transAmount24h, transCount7d, cancelRetryCount, riskTags, riskScore);
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
            hash.put("account_status", accountStatus);
            hash.put("login_count_24h", String.valueOf(loginCount24h));
            hash.put("trans_count_24h", String.valueOf(transCount24h));
            hash.put("trans_amount_24h", String.format(Locale.US, "%.2f", transAmount24h));
            hash.put("trans_count_7d", String.valueOf(transCount7d));
            hash.put("cancel_retry_count", String.valueOf(cancelRetryCount));
            hash.put("risk_tags", riskTags);
            hash.put("risk_score", String.valueOf(riskScore));
            return hash;
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: DwsToUserProfileJob <dws_user_input_path...> <profile_output_path>");
            return 2;
        }
        Path output = new Path(args[args.length - 1]);
        JobUtil.deleteOutputIfExists(getConf(), output);
        Job job = Job.getInstance(getConf(), "risk-offline-mr2-ext-dws-to-user-profile");
        job.setJarByClass(DwsToUserProfileJob.class);
        job.setMapperClass(ProfileMapper.class);
        job.setReducerClass(ProfileReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        for (int i = 0; i < args.length - 1; i++) {
            FileInputFormat.addInputPath(job, new Path(args[i]));
        }
        FileOutputFormat.setOutputPath(job, output);
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new DwsToUserProfileJob(), args));
    }
}
