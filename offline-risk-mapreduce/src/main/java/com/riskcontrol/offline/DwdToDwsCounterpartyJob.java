package com.riskcontrol.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DwdToDwsCounterpartyJob extends Configured implements Tool {
    public static class CounterpartyMapper extends Mapper<Object, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                CleanTransRecord r = CleanTransRecord.parse(value.toString());
                if (r.counterpartyId.isEmpty()) {
                    context.getCounter(OfflineConstants.COUNTER_GROUP, "dwd_cpty_missing_counterparty").increment(1L);
                    return;
                }
                outKey.set(r.counterpartyId);
                outValue.set(r.amount + "\t" + r.userId + "\t" + r.timestamp + "\t" + r.counterpartyName + "\t" + r.dt);
                context.write(outKey, outValue);
            } catch (Exception e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "dwd_cpty_bad_rows").increment(1L);
            }
        }
    }

    public static class CounterpartyReducer extends Reducer<Text, Text, NullWritable, Text> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final Text outValue = new Text();
        private Connection connection;
        private PreparedStatement upsertBlacklist;
        private Jedis jedis;

        @Override
        protected void setup(Context context) throws IOException {
            try {
                JdbcConfig jdbcConfig = JdbcConfig.from(context.getConfiguration(), "offline.blacklist.mysql.enabled");
                connection = jdbcConfig.open();
                if (connection != null) {
                    connection.setAutoCommit(false);
                    upsertBlacklist = connection.prepareStatement(
                            "INSERT INTO counterparty_blacklist "
                                    + "(counterparty_id,counterparty_name,risk_level,risk_type,source,total_received_24h,total_received_7d,unique_payers_24h,registration_days,risk_tags) "
                                    + "VALUES (?,?,?,?,?,?,?,?,?,?) "
                                    + "ON DUPLICATE KEY UPDATE "
                                    + "counterparty_name=VALUES(counterparty_name),risk_level=VALUES(risk_level),risk_type=VALUES(risk_type),source=VALUES(source),"
                                    + "total_received_24h=VALUES(total_received_24h),total_received_7d=VALUES(total_received_7d),"
                                    + "unique_payers_24h=VALUES(unique_payers_24h),risk_tags=VALUES(risk_tags),update_time=CURRENT_TIMESTAMP");
                }
                jedis = RedisConfig.from(context.getConfiguration()).open();
            } catch (Exception e) {
                throw new IOException("Failed to initialize blacklist sinks", e);
            }
        }

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            int count = 0;
            double total = 0D;
            double max = 0D;
            String name = "";
            String dt = context.getConfiguration().get("offline.dt", "");
            Set<String> payers = new HashSet<String>();
            for (Text value : values) {
                String[] p = value.toString().split("\t", -1);
                if (p.length < 5) {
                    continue;
                }
                try {
                    double amount = Double.parseDouble(p[0]);
                    total += amount;
                    max = Math.max(max, amount);
                    count++;
                    if (!p[1].isEmpty()) {
                        payers.add(p[1]);
                    }
                    if (name.isEmpty()) {
                        name = p[3];
                    }
                    if (dt.isEmpty()) {
                        dt = p[4];
                    }
                } catch (NumberFormatException e) {
                    context.getCounter(OfflineConstants.COUNTER_GROUP, "dwd_cpty_bad_numbers").increment(1L);
                }
            }
            if (count == 0) {
                return;
            }
            int uniquePayers = payers.size();
            double avg = total / count;
            ObjectNode node = MAPPER.createObjectNode();
            node.put("counterparty_id", key.toString());
            node.put("dt", dt);
            node.put("total_received", round2(total));
            node.put("unique_payers", uniquePayers);
            node.put("trans_count", count);
            node.put("max_single_amount", round2(max));
            node.put("avg_amount", round2(avg));
            outValue.set(MAPPER.writeValueAsString(node));
            context.write(NullWritable.get(), outValue);

            String riskLevel = riskLevel(uniquePayers, total);
            if (!riskLevel.isEmpty()) {
                writeBlacklist(key.toString(), name, riskLevel, total, uniquePayers, context);
            }
        }

        @Override
        protected void cleanup(Context context) throws IOException {
            try {
                if (upsertBlacklist != null) {
                    upsertBlacklist.executeBatch();
                }
                if (connection != null) {
                    connection.commit();
                    connection.close();
                }
                if (jedis != null) {
                    jedis.close();
                }
            } catch (SQLException e) {
                throw new IOException("Failed to close blacklist sinks", e);
            }
        }

        private void writeBlacklist(String counterpartyId, String name, String riskLevel,
                                    double total, int uniquePayers, Context context) throws IOException {
            try {
                if (upsertBlacklist != null) {
                    upsertBlacklist.setString(1, counterpartyId);
                    upsertBlacklist.setString(2, name);
                    upsertBlacklist.setString(3, riskLevel);
                    upsertBlacklist.setString(4, "money_laundering");
                    upsertBlacklist.setString(5, "internal_detection");
                    upsertBlacklist.setDouble(6, round2(total));
                    upsertBlacklist.setDouble(7, round2(total));
                    upsertBlacklist.setInt(8, uniquePayers);
                    upsertBlacklist.setInt(9, 999);
                    upsertBlacklist.setString(10, "multi_payer_auto_detected");
                    upsertBlacklist.addBatch();
                }
                if (jedis != null) {
                    Map<String, String> hash = new HashMap<String, String>();
                    hash.put("risk_level", riskLevel);
                    hash.put("risk_type", "money_laundering");
                    hash.put("total_received_24h", String.format(Locale.US, "%.2f", total));
                    hash.put("total_received_7d", String.format(Locale.US, "%.2f", total));
                    hash.put("unique_payers_24h", String.valueOf(uniquePayers));
                    hash.put("registration_days", "999");
                    hash.put("risk_tags", "multi_payer_auto_detected");
                    hash.put("fast_in_out_score", "0");
                    hash.put("status", "active");
                    jedis.hmset("counterparty:" + counterpartyId, hash);
                }
                context.getCounter(OfflineConstants.COUNTER_GROUP, "blacklist_marked_" + riskLevel).increment(1L);
            } catch (SQLException e) {
                throw new IOException("Failed to write blacklist row", e);
            }
        }

        private static String riskLevel(int uniquePayers, double total) {
            if (uniquePayers >= 20 && total > 500000D) {
                return "high";
            }
            if (uniquePayers >= 10 && total > 100000D) {
                return "medium";
            }
            return "";
        }

        private static double round2(double value) {
            return Double.parseDouble(String.format(Locale.US, "%.2f", value));
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: DwdToDwsCounterpartyJob <dwd_input_path> <dws_counterparty_output_path>");
            return 2;
        }
        Path output = new Path(args[1]);
        JobUtil.deleteOutputIfExists(getConf(), output);
        Job job = Job.getInstance(getConf(), "risk-offline-mr3-dwd-to-dws-counterparty");
        job.setJarByClass(DwdToDwsCounterpartyJob.class);
        job.setMapperClass(CounterpartyMapper.class);
        job.setReducerClass(CounterpartyReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, output);
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new DwdToDwsCounterpartyJob(), args));
    }
}
