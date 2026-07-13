package com.riskcontrol.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

/** Loads immutable ADS HDFS outputs into query tables consumed by the dashboard. */
public class AdsRiskToMysqlJob extends Configured implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> TABLES = Arrays.asList(
            "ads_transaction_risk_detail", "ads_offline_overview_metrics", "ads_offline_score_distribution",
            "ads_province_risk_rank", "ads_city_risk_rank", "ads_rule_risk_rank", "ads_risk_time_trend",
            "ads_risk_behavior_distribution", "ads_risk_feature_distribution", "ads_high_risk_transaction", "ads_high_risk_user_rank",
            "ads_device_risk_rank", "ads_counterparty_risk_rank", "ads_cross_region_risk_flow");

    @Override
    public int run(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: AdsRiskToMysqlJob <ads_detail_hdfs_path> <ads_dashboard_hdfs_path> [ads_cross_region_flow_hdfs_path]");
            return 2;
        }
        JdbcConfig config = JdbcConfig.from(getConf(), "offline.ads.mysql.enabled");
        if (!config.enabled) {
            System.out.println("ADS MySQL import disabled by offline.ads.mysql.enabled=false");
            return 0;
        }
        String dt = getConf().get("offline.dt", "");
        FileSystem fs = new Path(args[0]).getFileSystem(getConf());
        try (Connection connection = config.open()) {
            connection.setAutoCommit(false);
            createTables(connection);
            deletePartition(connection, dt);
            long detailRows = importFiles(fs, new Path(args[0]), "detail", connection);
            long dashboardRows = importFiles(fs, new Path(args[1]), "", connection);
            if (args.length == 3) {
                dashboardRows += importFiles(fs, new Path(args[2]), "cross_region_risk_flow", connection);
            }
            connection.commit();
            System.out.println("ads_mysql_detail_rows=" + detailRows);
            System.out.println("ads_mysql_dashboard_rows=" + dashboardRows);
            System.out.println("ads_mysql_dt=" + dt);
        }
        return 0;
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS ads_transaction_risk_detail ("
                    + "dt VARCHAR(8) NOT NULL, window_start VARCHAR(32), window_end VARCHAR(32), trans_id VARCHAR(64) NOT NULL,"
                    + "user_id VARCHAR(64), province VARCHAR(64), city VARCHAR(64), risk_score INT, raw_risk_score INT,"
                    + "risk_level VARCHAR(16), hit_rules VARCHAR(1000), rule_categories VARCHAR(500), amount DOUBLE,"
                    + "channel VARCHAR(64), trans_type VARCHAR(64), event_time BIGINT, device_id VARCHAR(64), counterparty_id VARCHAR(64),"
                    + "PRIMARY KEY(dt,trans_id), INDEX idx_atrd_dt_level(dt,risk_level), INDEX idx_atrd_dt_city(dt,city))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_offline_overview_metrics ("
                    + "dt VARCHAR(8) PRIMARY KEY, window_start VARCHAR(32), window_end VARCHAR(32), total_transactions BIGINT,"
                    + "risk_transactions BIGINT, high_risk_transactions BIGINT, extreme_risk_transactions BIGINT, distinct_users BIGINT,"
                    + "risk_users BIGINT, high_risk_users BIGINT, distinct_devices BIGINT, distinct_counterparties BIGINT,"
                    + "total_amount DOUBLE, risk_amount DOUBLE, avg_risk_score DOUBLE, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_offline_score_distribution ("
                    + "dt VARCHAR(8) NOT NULL, score_bucket VARCHAR(16) NOT NULL, risk_count BIGINT, sort_order INT,"
                    + "PRIMARY KEY(dt,score_bucket))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_province_risk_rank ("
                    + "dt VARCHAR(8) NOT NULL, region_name VARCHAR(64) NOT NULL, risk_count BIGINT, high_risk_count BIGINT,"
                    + "extreme_risk_count BIGINT, risk_amount DOUBLE, avg_risk_score DOUBLE, PRIMARY KEY(dt,region_name))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_city_risk_rank ("
                    + "dt VARCHAR(8) NOT NULL, region_name VARCHAR(64) NOT NULL, risk_count BIGINT, high_risk_count BIGINT,"
                    + "extreme_risk_count BIGINT, risk_amount DOUBLE, avg_risk_score DOUBLE, PRIMARY KEY(dt,region_name))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_rule_risk_rank ("
                    + "dt VARCHAR(8) NOT NULL, rule_code VARCHAR(16) NOT NULL, rule_name VARCHAR(64), rule_category VARCHAR(32),"
                    + "risk_count BIGINT, high_risk_count BIGINT, extreme_risk_count BIGINT, risk_amount DOUBLE, avg_risk_score DOUBLE,"
                    + "PRIMARY KEY(dt,rule_code))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_risk_time_trend ("
                    + "dt VARCHAR(8) NOT NULL, stat_hour VARCHAR(16) NOT NULL, risk_count BIGINT, high_risk_count BIGINT,"
                    + "extreme_risk_count BIGINT, risk_amount DOUBLE, avg_risk_score DOUBLE, PRIMARY KEY(dt,stat_hour))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_risk_behavior_distribution ("
                    + "dt VARCHAR(8) NOT NULL, behavior_type VARCHAR(16) NOT NULL, behavior_name VARCHAR(64) NOT NULL,"
                    + "risk_count BIGINT, high_risk_count BIGINT, extreme_risk_count BIGINT, risk_amount DOUBLE, avg_risk_score DOUBLE,"
                    + "PRIMARY KEY(dt,behavior_type,behavior_name))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_risk_feature_distribution ("
                    + "dt VARCHAR(8) NOT NULL, feature_key VARCHAR(32) NOT NULL, feature_value VARCHAR(64) NOT NULL,"
                    + "total_count BIGINT, risk_count BIGINT, high_risk_count BIGINT, extreme_risk_count BIGINT,"
                    + "risk_amount DOUBLE, avg_risk_score DOUBLE, risk_rate DOUBLE, high_risk_rate DOUBLE,"
                    + "PRIMARY KEY(dt,feature_key,feature_value), INDEX idx_arfd_dt_feature(dt,feature_key))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_high_risk_transaction ("
                    + "dt VARCHAR(8) NOT NULL, trans_id VARCHAR(64) NOT NULL, user_id VARCHAR(64), province VARCHAR(64), city VARCHAR(64),"
                    + "risk_score INT, risk_level VARCHAR(16), hit_rules VARCHAR(1000), amount DOUBLE, channel VARCHAR(64),"
                    + "trans_type VARCHAR(64), event_time BIGINT, PRIMARY KEY(dt,trans_id), INDEX idx_ahrt_dt_score(dt,risk_score))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_high_risk_user_rank ("
                    + "dt VARCHAR(8) NOT NULL, entity_id VARCHAR(64) NOT NULL, risk_count BIGINT, high_risk_count BIGINT,"
                    + "extreme_risk_count BIGINT, risk_amount DOUBLE, avg_risk_score DOUBLE, PRIMARY KEY(dt,entity_id))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_device_risk_rank ("
                    + "dt VARCHAR(8) NOT NULL, entity_id VARCHAR(64) NOT NULL, risk_count BIGINT, high_risk_count BIGINT,"
                    + "extreme_risk_count BIGINT, risk_amount DOUBLE, avg_risk_score DOUBLE, PRIMARY KEY(dt,entity_id))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_counterparty_risk_rank ("
                    + "dt VARCHAR(8) NOT NULL, entity_id VARCHAR(64) NOT NULL, risk_count BIGINT, high_risk_count BIGINT,"
                    + "extreme_risk_count BIGINT, risk_amount DOUBLE, avg_risk_score DOUBLE, PRIMARY KEY(dt,entity_id))");
            statement.execute("CREATE TABLE IF NOT EXISTS ads_cross_region_risk_flow ("
                    + "dt VARCHAR(8) NOT NULL, from_province VARCHAR(64) NOT NULL, from_city VARCHAR(64) NOT NULL,"
                    + "to_province VARCHAR(64) NOT NULL, to_city VARCHAR(64) NOT NULL, risk_count BIGINT, high_risk_count BIGINT,"
                    + "extreme_risk_count BIGINT, risk_amount DOUBLE, avg_risk_score DOUBLE,"
                    + "PRIMARY KEY(dt,from_province,from_city,to_province,to_city))");
        }
    }

    private void deletePartition(Connection connection, String dt) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : TABLES) {
                statement.executeUpdate("DELETE FROM " + table + " WHERE dt='" + dt.replace("'", "''") + "'");
            }
        }
    }

    private long importFiles(FileSystem fs, Path path, String explicitKind, Connection connection) throws Exception {
        if (!fs.exists(path)) throw new IOException("ADS input path does not exist: " + path);
        return importPath(fs, path, explicitKind, connection);
    }

    private long importPath(FileSystem fs, Path path, String kind, Connection connection) throws Exception {
        long rows = 0;
        for (FileStatus status : fs.listStatus(path)) {
            Path child = status.getPath();
            String name = child.getName();
            if (status.isDirectory()) {
                rows += importPath(fs, child, kind.isEmpty() ? name : kind, connection);
            } else if (status.isFile() && name.startsWith("part")) {
                rows += importPartFile(fs, child, kind, connection);
            }
        }
        return rows;
    }

    private long importPartFile(FileSystem fs, Path path, String kind, Connection connection) throws Exception {
        String table = tableFor(kind);
        if (table == null) return 0;
        String sql = insertSql(table);
        long rows = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fs.open(path), StandardCharsets.UTF_8))) {
            String line;
            int pending = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                JsonNode node = MAPPER.readTree(line);
                bind(statement, table, node);
                statement.addBatch();
                rows++; pending++;
                if (pending >= 1000) {
                    statement.executeBatch();
                    connection.commit();
                    pending = 0;
                }
            }
            if (pending > 0) statement.executeBatch();
        }
        return rows;
    }

    private static String tableFor(String kind) {
        if ("detail".equals(kind)) return "ads_transaction_risk_detail";
        if ("overview".equals(kind)) return "ads_offline_overview_metrics";
        if ("score_distribution".equals(kind)) return "ads_offline_score_distribution";
        if ("province_risk_rank".equals(kind)) return "ads_province_risk_rank";
        if ("city_risk_rank".equals(kind)) return "ads_city_risk_rank";
        if ("rule_risk_rank".equals(kind)) return "ads_rule_risk_rank";
        if ("risk_time_trend".equals(kind)) return "ads_risk_time_trend";
        if ("risk_behavior_distribution".equals(kind)) return "ads_risk_behavior_distribution";
        if ("risk_feature_distribution".equals(kind)) return "ads_risk_feature_distribution";
        if ("high_risk_transaction".equals(kind)) return "ads_high_risk_transaction";
        if ("high_risk_user_rank".equals(kind)) return "ads_high_risk_user_rank";
        if ("device_risk_rank".equals(kind)) return "ads_device_risk_rank";
        if ("counterparty_risk_rank".equals(kind)) return "ads_counterparty_risk_rank";
        if ("cross_region_risk_flow".equals(kind)) return "ads_cross_region_risk_flow";
        return null;
    }

    private static String insertSql(String table) {
        if ("ads_transaction_risk_detail".equals(table)) return "INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        if ("ads_offline_overview_metrics".equals(table)) return "INSERT INTO " + table + " (dt,window_start,window_end,total_transactions,risk_transactions,high_risk_transactions,extreme_risk_transactions,distinct_users,risk_users,high_risk_users,distinct_devices,distinct_counterparties,total_amount,risk_amount,avg_risk_score) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        if ("ads_offline_score_distribution".equals(table)) return "INSERT INTO " + table + " VALUES (?,?,?,?)";
        if ("ads_province_risk_rank".equals(table) || "ads_city_risk_rank".equals(table)) return "INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?)";
        if ("ads_rule_risk_rank".equals(table)) return "INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?,?,?)";
        if ("ads_risk_time_trend".equals(table)) return "INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?)";
        if ("ads_risk_behavior_distribution".equals(table)) return "INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?,?)";
        if ("ads_risk_feature_distribution".equals(table)) return "INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        if ("ads_high_risk_user_rank".equals(table) || "ads_device_risk_rank".equals(table) || "ads_counterparty_risk_rank".equals(table)) return "INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?)";
        if ("ads_cross_region_risk_flow".equals(table)) return "INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?,?,?,?)";
        return "INSERT INTO " + table + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
    }

    private static void bind(PreparedStatement s, String table, JsonNode n) throws SQLException {
        if ("ads_transaction_risk_detail".equals(table)) {
            int i = 1; s.setString(i++, text(n,"dt")); s.setString(i++,text(n,"window_start")); s.setString(i++,text(n,"window_end"));
            s.setString(i++,text(n,"trans_id")); s.setString(i++,text(n,"user_id")); s.setString(i++,text(n,"province")); s.setString(i++,text(n,"city"));
            s.setInt(i++,integer(n,"risk_score")); s.setInt(i++,integer(n,"raw_risk_score")); s.setString(i++,text(n,"risk_level"));
            s.setString(i++,json(n,"rule_codes")); s.setString(i++,json(n,"rule_categories")); s.setDouble(i++,decimal(n,"amount"));
            s.setString(i++,text(n,"channel")); s.setString(i++,text(n,"trans_type")); s.setLong(i++,longValue(n,"event_time"));
            s.setString(i++,text(n,"device_id")); s.setString(i,text(n,"counterparty_id")); return;
        }
        if ("ads_offline_overview_metrics".equals(table)) {
            int i = 1; s.setString(i++,text(n,"dt")); s.setString(i++,text(n,"window_start")); s.setString(i++,text(n,"window_end"));
            for (String field : new String[]{"total_transactions","risk_transactions","high_risk_transactions","extreme_risk_transactions","distinct_users","risk_users","high_risk_users","distinct_devices","distinct_counterparties"}) s.setLong(i++,longValue(n,field));
            s.setDouble(i++,decimal(n,"total_amount")); s.setDouble(i++,decimal(n,"risk_amount")); s.setDouble(i,decimal(n,"avg_risk_score")); return;
        }
        if ("ads_offline_score_distribution".equals(table)) { s.setString(1,text(n,"dt")); s.setString(2,text(n,"score_bucket")); s.setLong(3,longValue(n,"risk_count")); s.setInt(4,integer(n,"sort_order")); return; }
        if ("ads_province_risk_rank".equals(table) || "ads_city_risk_rank".equals(table)) { s.setString(1,text(n,"dt")); s.setString(2,text(n,"region_name")); bindAggregate(s,3,n); return; }
        if ("ads_rule_risk_rank".equals(table)) { s.setString(1,text(n,"dt")); s.setString(2,text(n,"rule_code")); s.setString(3,text(n,"rule_name")); s.setString(4,text(n,"rule_category")); bindAggregate(s,5,n); return; }
        if ("ads_risk_time_trend".equals(table)) { s.setString(1,text(n,"dt")); s.setString(2,text(n,"stat_hour")); bindAggregate(s,3,n); return; }
        if ("ads_risk_behavior_distribution".equals(table)) { s.setString(1,text(n,"dt")); s.setString(2,text(n,"behavior_type")); s.setString(3,text(n,"behavior_name")); bindAggregate(s,4,n); return; }
        if ("ads_risk_feature_distribution".equals(table)) {
            s.setString(1,text(n,"dt")); s.setString(2,text(n,"feature_key")); s.setString(3,text(n,"feature_value"));
            s.setLong(4,longValue(n,"total_count")); bindAggregate(s,5,n);
            s.setDouble(10,decimal(n,"risk_rate")); s.setDouble(11,decimal(n,"high_risk_rate")); return;
        }
        if ("ads_high_risk_user_rank".equals(table) || "ads_device_risk_rank".equals(table) || "ads_counterparty_risk_rank".equals(table)) { s.setString(1,text(n,"dt")); s.setString(2,text(n,"entity_id")); bindAggregate(s,3,n); return; }
        if ("ads_cross_region_risk_flow".equals(table)) { s.setString(1,text(n,"dt")); s.setString(2,text(n,"from_province")); s.setString(3,text(n,"from_city")); s.setString(4,text(n,"to_province")); s.setString(5,text(n,"to_city")); bindAggregate(s,6,n); return; }
        int i = 1; s.setString(i++,text(n,"dt")); s.setString(i++,text(n,"trans_id")); s.setString(i++,text(n,"user_id")); s.setString(i++,text(n,"province")); s.setString(i++,text(n,"city")); s.setInt(i++,integer(n,"risk_score")); s.setString(i++,text(n,"risk_level")); s.setString(i++,json(n,"rule_codes")); s.setDouble(i++,decimal(n,"amount")); s.setString(i++,text(n,"channel")); s.setString(i++,text(n,"trans_type")); s.setLong(i,longValue(n,"event_time"));
    }

    private static void bindAggregate(PreparedStatement statement, int index, JsonNode node) throws SQLException {
        statement.setLong(index++, longValue(node,"risk_count")); statement.setLong(index++,longValue(node,"high_risk_count"));
        statement.setLong(index++,longValue(node,"extreme_risk_count")); statement.setDouble(index++,decimal(node,"risk_amount")); statement.setDouble(index,decimal(node,"avg_risk_score"));
    }
    private static String text(JsonNode n,String f){JsonNode v=n.get(f);return v==null||v.isNull()?"":TextUtil.clean(v.asText());}
    private static String json(JsonNode n,String f){JsonNode v=n.get(f);try{return v==null?"[]":MAPPER.writeValueAsString(v);}catch(Exception e){return "[]";}}
    private static int integer(JsonNode n,String f){JsonNode v=n.get(f);return v==null?0:v.asInt();}
    private static long longValue(JsonNode n,String f){JsonNode v=n.get(f);return v==null?0L:v.asLong();}
    private static double decimal(JsonNode n,String f){JsonNode v=n.get(f);return v==null?0D:v.asDouble();}

    public static void main(String[] args) throws Exception { System.exit(ToolRunner.run(new AdsRiskToMysqlJob(), args)); }
}
