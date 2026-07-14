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
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DwdToDwsDeviceJob extends Configured implements Tool {
    public static class DeviceMapper extends Mapper<Object, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                CleanTransRecord r = CleanTransRecord.parse(value.toString());
                if (r.deviceId.isEmpty()) {
                    context.getCounter(OfflineConstants.COUNTER_GROUP, "dwd_device_missing_device").increment(1L);
                    return;
                }
                outKey.set(r.deviceId);
                outValue.set(r.userId + "\t" + r.devScore + "\t" + (r.rootJailbreak ? 1 : 0) + "\t" + r.city + "\t" + r.dt);
                context.write(outKey, outValue);
            } catch (Exception e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "dwd_device_bad_rows").increment(1L);
            }
        }
    }

    public static class DeviceReducer extends Reducer<Text, Text, NullWritable, Text> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final Text outValue = new Text();
        private Jedis jedis;
        private Pipeline pipeline;
        private int sharedTtlSeconds;
        private int pendingRedisWrites;

        @Override
        protected void setup(Context context) {
            try {
                jedis = RedisConfig.from(context.getConfiguration()).open();
                if (jedis != null) {
                    pipeline = jedis.pipelined();
                }
            } catch (JedisException e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "device_redis_disabled").increment(1L);
                jedis = null;
                pipeline = null;
            }
            sharedTtlSeconds = context.getConfiguration().getInt("offline.device.shared.ttl.seconds", 86400);
        }

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            Set<String> users = new HashSet<String>();
            Set<String> cities = new HashSet<String>();
            int count = 0;
            int rootCount = 0;
            int scoreSum = 0;
            String dt = context.getConfiguration().get("offline.dt", "");
            for (Text value : values) {
                String[] p = value.toString().split("\t", -1);
                if (p.length < 5) {
                    continue;
                }
                try {
                    users.add(p[0]);
                    scoreSum += Integer.parseInt(p[1]);
                    rootCount += Integer.parseInt(p[2]);
                    if (!p[3].isEmpty()) {
                        cities.add(p[3]);
                    }
                    if (dt.isEmpty()) {
                        dt = p[4];
                    }
                    count++;
                } catch (NumberFormatException e) {
                    context.getCounter(OfflineConstants.COUNTER_GROUP, "dwd_device_bad_numbers").increment(1L);
                }
            }
            if (count == 0) {
                return;
            }
            int associatedUsers = users.size();
            ObjectNode node = MAPPER.createObjectNode();
            node.put("device_id", key.toString());
            node.put("dt", dt);
            node.put("associated_users", associatedUsers);
            node.put("trans_count", count);
            node.put("avg_dev_score", Double.parseDouble(String.format(Locale.US, "%.2f", scoreSum * 1.0D / count)));
            node.put("root_jailbreak_count", rootCount);
            node.put("distinct_cities", cities.size());
            outValue.set(MAPPER.writeValueAsString(node));
            context.write(NullWritable.get(), outValue);

            writeRedis(key.toString(), associatedUsers, count, scoreSum, rootCount, cities.size(), context);
        }

        @Override
        protected void cleanup(Context context) {
            if (pipeline != null) {
                try {
                    pipeline.sync();
                } catch (JedisException e) {
                    context.getCounter(OfflineConstants.COUNTER_GROUP, "device_redis_flush_failed").increment(1L);
                }
            }
            if (jedis != null) {
                jedis.close();
            }
        }

        private void writeRedis(String deviceId, int associatedUsers, int count, int scoreSum,
                                int rootCount, int cityCount, Context context) {
            if (pipeline == null) {
                return;
            }
            try {
                Map<String, String> hash = new HashMap<String, String>();
                hash.put("associated_users_30d", String.valueOf(associatedUsers));
                hash.put("trans_count_30d", String.valueOf(count));
                hash.put("avg_dev_score", String.format(Locale.US, "%.2f", scoreSum * 1.0D / count));
                hash.put("root_jailbreak_count", String.valueOf(rootCount));
                hash.put("distinct_cities", String.valueOf(cityCount));
                pipeline.hmset("device_risk:" + deviceId, hash);
                if (associatedUsers >= 5) {
                    pipeline.setex("device_shared:" + deviceId, sharedTtlSeconds, "true");
                    context.getCounter(OfflineConstants.COUNTER_GROUP, "device_shared_marked").increment(1L);
                }
                pendingRedisWrites++;
                if (pendingRedisWrites >= 1000) {
                    pipeline.sync();
                    pendingRedisWrites = 0;
                }
            } catch (JedisException e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "device_redis_write_failed").increment(1L);
                try {
                    pipeline.sync();
                } catch (JedisException ignored) {
                    // Redis is an acceleration sink; keep HDFS output successful.
                }
                pipeline = null;
            }
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: DwdToDwsDeviceJob <dwd_input_path> <dws_device_output_path>");
            return 2;
        }
        Path output = new Path(args[1]);
        JobUtil.deleteOutputIfExists(getConf(), output);
        Job job = Job.getInstance(getConf(), "risk-offline-mr4-dwd-to-dws-device");
        job.setJarByClass(DwdToDwsDeviceJob.class);
        job.setMapperClass(DeviceMapper.class);
        job.setReducerClass(DeviceReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, output);
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new DwdToDwsDeviceJob(), args));
    }
}
