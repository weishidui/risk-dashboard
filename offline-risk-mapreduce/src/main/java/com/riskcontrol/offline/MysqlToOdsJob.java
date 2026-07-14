package com.riskcontrol.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

public class MysqlToOdsJob extends Configured implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: MysqlToOdsJob <dt> <ods_output_dir>");
            return 2;
        }

        String dt = args[0];
        Path outputDir = new Path(args[1]);
        Path outputFile = new Path(outputDir, "FlumeData.transaction_history.full.json");
        FileSystem fs = outputDir.getFileSystem(getConf());
        if (fs.exists(outputDir)) {
            fs.delete(outputDir, true);
        }
        fs.mkdirs(outputDir);

        JdbcConfig jdbcConfig = JdbcConfig.from(getConf(), "offline.mysql.to.ods.enabled");
        String sql = getConf().get("offline.mysql.to.ods.sql",
                "SELECT trans_id,user_id,amount,trans_timestamp,city,geo_location,device_id,network_type,dev_score,"
                        + "ip_address,os_type,os_version,screen_resolution,battery_level,root_jailbreak,sim_operator,user_agent,"
                        + "dns_server,wifi_ssid,trans_type,pay_channel,input_method,click_duration,note,page_url,"
                        + "counterparty_id,counterparty_name,counterparty_bank,login_session_id,login_fail_count,cancel_retry_count "
                        + "FROM transaction_history ORDER BY id");

        int rows = 0;
        Set<String> users = new HashSet<String>();
        try (Connection connection = jdbcConfig.open();
             Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             FSDataOutputStream out = fs.create(outputFile, true);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            statement.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData meta = resultSet.getMetaData();
                while (resultSet.next()) {
                    ObjectNode node = MAPPER.createObjectNode();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String name = meta.getColumnLabel(i);
                        putValue(node, name, resultSet.getObject(i));
                    }
                    long timestamp = resultSet.getLong("trans_timestamp");
                    node.put("timestamp", timestamp);
                    node.put("dt", dt);
                    String userId = resultSet.getString("user_id");
                    if (userId != null && !userId.isEmpty()) {
                        users.add(userId);
                    }
                    writer.write(MAPPER.writeValueAsString(node));
                    writer.newLine();
                    rows++;
                }
            }
        }

        System.out.println("mysql_to_ods_rows=" + rows);
        System.out.println("mysql_to_ods_users=" + users.size());
        System.out.println("mysql_to_ods_output=" + outputFile);
        return 0;
    }

    private static void putValue(ObjectNode node, String name, Object value) {
        if (value == null) {
            node.put(name, "");
        } else if (value instanceof Integer) {
            node.put(name, (Integer) value);
        } else if (value instanceof Long) {
            node.put(name, (Long) value);
        } else if (value instanceof Float) {
            node.put(name, ((Float) value).doubleValue());
        } else if (value instanceof Double) {
            node.put(name, (Double) value);
        } else if (value instanceof BigDecimal) {
            node.put(name, ((BigDecimal) value).doubleValue());
        } else if (value instanceof Boolean) {
            node.put(name, (Boolean) value);
        } else if (value instanceof Timestamp) {
            node.put(name, ((Timestamp) value).getTime());
        } else {
            node.put(name, TextUtil.clean(String.valueOf(value)));
        }
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new MysqlToOdsJob(), args));
    }
}
