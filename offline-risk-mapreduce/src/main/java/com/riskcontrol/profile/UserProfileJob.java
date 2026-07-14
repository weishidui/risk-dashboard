package com.riskcontrol.profile;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.db.DBConfiguration;
import org.apache.hadoop.mapreduce.lib.db.DBInputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class UserProfileJob extends Configured implements Tool {
    @Override
    public int run(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: UserProfileJob <output_path> [input_path]");
            return 2;
        }

        Configuration conf = getConf();
        Path outputPath = new Path(args[0]);
        FileSystem fs = outputPath.getFileSystem(conf);
        if (fs.exists(outputPath)) {
            fs.delete(outputPath, true);
        }

        Job job = Job.getInstance(conf, "risk-control-user-profile-mapreduce");
        job.setJarByClass(UserProfileJob.class);
        job.setReducerClass(UserProfileReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        if (conf.getBoolean("profile.db.input.enabled", true)) {
            configureDbInput(job);
        } else {
            if (args.length < 2) {
                System.err.println("Usage with -D profile.db.input.enabled=false: UserProfileJob <output_path> <input_path>");
                return 2;
            }
            job.setMapperClass(UserProfileMapper.class);
            FileInputFormat.addInputPath(job, new Path(args[1]));
        }
        FileOutputFormat.setOutputPath(job, outputPath);

        boolean success = job.waitForCompletion(true);
        if (!success) {
            return 1;
        }

        if (conf.getBoolean("profile.mysql.import.enabled", true)) {
            int imported = UserProfileMysqlImporter.importOutput(conf, outputPath);
            System.out.println("Imported user_profile rows: " + imported);
        }
        return 0;
    }

    public static void main(String[] args) throws Exception {
        int code = ToolRunner.run(new UserProfileJob(), args);
        System.exit(code);
    }

    private static void configureDbInput(Job job) {
        Configuration conf = job.getConfiguration();
        String jdbcUrl = conf.get("profile.jdbc.url");
        String jdbcUser = conf.get("profile.jdbc.user", "root");
        String jdbcPassword = conf.get("profile.jdbc.password", "123456");
        String tableName = conf.get("profile.history.table", "transaction_history");
        String condition = conf.get("profile.history.condition", "1=1");
        String orderBy = conf.get("profile.history.order.by", "id");

        DBConfiguration.configureDB(conf, "com.mysql.jdbc.Driver", jdbcUrl, jdbcUser, jdbcPassword);
        job.setInputFormatClass(DBInputFormat.class);
        job.setMapperClass(UserProfileDbMapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        DBInputFormat.setInput(job,
                TransactionHistoryRecord.class,
                tableName,
                condition,
                orderBy,
                "trans_id", "user_id", "amount", "trans_timestamp", "city", "device_id");
    }
}
