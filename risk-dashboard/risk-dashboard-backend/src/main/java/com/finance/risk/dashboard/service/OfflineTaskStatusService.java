package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.dao.UserProfileDao;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class OfflineTaskStatusService {

    private static final Logger log = LoggerFactory.getLogger(OfflineTaskStatusService.class);

    private static final String DEFAULT_BASE = "/user/hive/warehouse/risk_control.db";
    private static final long LATEST_DT_CACHE_MS = 30_000L;
    private static final long TASK_STATUS_CACHE_MS = 300_000L;

    @Value("${risk.offline.hdfs.ssh.host:192.168.125.100}")
    private String sshHost;

    @Value("${risk.offline.hdfs.ssh.port:22}")
    private int sshPort;

    @Value("${risk.offline.hdfs.ssh.username:master0}")
    private String sshUsername;

    @Value("${risk.offline.hdfs.ssh.password:123456}")
    private String sshPassword;

    @Value("${risk.offline.hdfs.base-path:" + DEFAULT_BASE + "}")
    private String hdfsBasePath;

    @Value("${risk.offline.hdfs.java-home:/home/master0/jdk1.8.0_171}")
    private String javaHome;

    @Value("${risk.offline.hdfs.hadoop-home:/home/master0/hadoop-2.7.6}")
    private String hadoopHome;

    @Value("${risk.offline.hdfs.command-timeout-ms:60000}")
    private long commandTimeoutMs;

    @Resource
    private UserProfileDao userProfileDao;

    private volatile String cachedLatestDt;
    private volatile long cachedLatestAt;
    private volatile List<Map<String, Object>> cachedTaskStatus;
    private volatile String cachedTaskDt;
    private volatile long cachedTaskAt;
    private volatile boolean refreshInProgress;

    private final Object refreshLock = new Object();

    public List<Map<String, Object>> getTaskStatus(String dtParam) {
        String dt = normalizeDt(dtParam);
        List<Map<String, Object>> cached = getFreshCachedTaskStatus(dt);
        if (cached != null) {
            return cached;
        }

        String targetDt = dt == null ? getCachedLatestDt("UNKNOWN") : dt;
        refreshTaskStatusAsync(dt);
        if (cachedTaskStatus != null && !cachedTaskStatus.isEmpty()
                && (dt == null || dt.equals(cachedTaskDt))) {
            return cloneTasks(cachedTaskStatus);
        }

        List<Map<String, Object>> checking = buildCheckingHdfsTasks(targetDt);
        checking.add(buildMysqlUserProfileTask(targetDt));
        return checking;
    }

    public String resolveLatestDt(String fallback) {
        try {
            String latest = resolveLatestDt();
            return latest != null && !latest.isEmpty() ? latest : fallback;
        } catch (Exception e) {
            log.warn("查询最新 HDFS 分区失败，使用兜底 dt={}: {}", fallback, e.getMessage());
            return fallback;
        }
    }

    public String getCachedLatestDt(String fallback) {
        return cachedLatestDt != null && !cachedLatestDt.isEmpty() ? cachedLatestDt : fallback;
    }

    private String resolveLatestDt() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedLatestDt != null && now - cachedLatestAt < LATEST_DT_CACHE_MS) {
            return cachedLatestDt;
        }

        String script = hadoopEnv()
                + "BASE=" + shellQuote(hdfsBasePath) + "\n"
                + "latest=''\n"
                + "for layer in ads_user_profile dws_user_transaction_daily dwd_trans_event ods_trans_event; do\n"
                + "  latest=$(hdfs dfs -ls \"$BASE/$layer\" 2>/dev/null | awk '{print $8}' | grep '/dt=' | sed 's#.*/dt=##' | sort | tail -1)\n"
                + "  [ -n \"$latest\" ] && break\n"
                + "done\n"
                + "echo \"DT|$latest\"\n";
        String output = runRemote(script);
        for (String line : output.split("\\r?\\n")) {
            if (line.startsWith("DT|")) {
                String latest = line.substring(3).trim();
                if (!latest.isEmpty()) {
                    cachedLatestDt = latest;
                    cachedLatestAt = now;
                    return latest;
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> getFreshCachedTaskStatus(String dt) {
        List<Map<String, Object>> cached = cachedTaskStatus;
        long now = System.currentTimeMillis();
        if (cached != null && now - cachedTaskAt < TASK_STATUS_CACHE_MS
                && (dt == null || dt.equals(cachedTaskDt))) {
            return cloneTasks(cached);
        }
        return null;
    }

    private void refreshTaskStatusAsync(String requestedDt) {
        synchronized (refreshLock) {
            if (refreshInProgress) {
                return;
            }
            refreshInProgress = true;
        }

        Thread worker = new Thread(() -> {
            String dt = requestedDt == null ? getCachedLatestDt("UNKNOWN") : requestedDt;
            try {
                if (requestedDt == null) {
                    String latestDt = resolveLatestDt();
                    if (latestDt != null && !latestDt.isEmpty()) {
                        dt = latestDt;
                    }
                }
                List<Map<String, Object>> tasks = queryHdfsTasks(dt);
                tasks.add(buildMysqlUserProfileTask(dt));
                cachedTaskDt = dt;
                cachedTaskAt = System.currentTimeMillis();
                cachedTaskStatus = cloneTasks(tasks);
            } catch (Exception e) {
                log.warn("refresh offline task status failed: {}", e.getMessage());
                List<Map<String, Object>> tasks = buildUnknownHdfsTasks(dt, e.getMessage());
                tasks.add(buildMysqlUserProfileTask(dt));
                cachedTaskDt = dt;
                cachedTaskAt = System.currentTimeMillis();
                cachedTaskStatus = cloneTasks(tasks);
            } finally {
                refreshInProgress = false;
            }
        }, "offline-task-status-refresh");
        worker.setDaemon(true);
        worker.start();
    }

    private List<Map<String, Object>> queryHdfsTasks(String dt) throws Exception {
        String script = hadoopEnv()
                + "BASE=" + shellQuote(hdfsBasePath) + "\n"
                + "DT=" + shellQuote(dt) + "\n"
                + "emit() {\n"
                + "  key=\"$1\"; name=\"$2\"; path=\"$3\"; mode=\"$4\"\n"
                + "  exists=no; success=no; files=0; bytes=0\n"
                + "  listing=$(hdfs dfs -ls -R \"$path\" 2>/dev/null)\n"
                + "  if [ $? -eq 0 ]; then\n"
                + "    exists=yes\n"
                + "    files=$(echo \"$listing\" | awk '$1 ~ /^-/ {c++} END {print c+0}')\n"
                + "    bytes=$(echo \"$listing\" | awk '$1 ~ /^-/ {s+=$5} END {print s+0}')\n"
                + "    echo \"$listing\" | awk '{print $8}' | grep -qx \"$path/_SUCCESS\" && success=yes\n"
                + "  fi\n"
                + "  echo \"TASK|$key|$name|$path|$mode|$exists|$success|${files:-0}|${bytes:-0}\"\n"
                + "}\n"
                + "emit ods_trans_event 'ODS 交易原始数据' \"$BASE/ods_trans_event/dt=$DT\" data\n"
                + "emit dwd_trans_event 'DWD 交易明细' \"$BASE/dwd_trans_event/dt=$DT\" success\n"
                + "emit dws_user_transaction_daily 'DWS 用户日汇总' \"$BASE/dws_user_transaction_daily/dt=$DT\" success\n"
                + "emit dws_counterparty_daily 'DWS 收款方日汇总' \"$BASE/dws_counterparty_daily/dt=$DT\" success\n"
                + "emit dws_device_daily 'DWS 设备日汇总' \"$BASE/dws_device_daily/dt=$DT\" success\n"
                + "emit dws_user_risk_daily 'DWS 用户风险汇总' \"$BASE/dws_user_risk_daily/dt=$DT\" success\n"
                + "emit ads_user_profile 'ADS 用户画像' \"$BASE/ads_user_profile/dt=$DT\" success\n"
                + "emit ads_transaction_risk_detail 'ADS 交易风险明细' \"$BASE/ads_transaction_risk_detail/dt=$DT\" success\n"
                + "emit ads_risk_dashboard 'ADS 风险看板聚合' \"$BASE/ads_risk_dashboard/dt=$DT\" success\n"
                + "emit ads_cross_region_risk_flow 'ADS 跨省风险流向' \"$BASE/ads_cross_region_risk_flow/dt=$DT\" success\n";

        String output = runRemote(script);
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            if (!line.startsWith("TASK|")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length < 9) {
                continue;
            }
            String key = parts[1];
            String name = parts[2];
            String path = parts[3];
            String mode = parts[4];
            boolean exists = "yes".equals(parts[5]);
            boolean successFile = "yes".equals(parts[6]);
            long files = parseLong(parts[7]);
            long bytes = parseLong(parts[8]);

            boolean ok = "success".equals(mode) ? exists && successFile : exists && files > 0 && bytes > 0;
            String status;
            if (ok) {
                status = "SUCCESS";
            } else if (!exists) {
                status = "MISSING";
            } else if ("success".equals(mode) && !successFile) {
                status = "NO_SUCCESS";
            } else {
                status = "EMPTY";
            }

            Map<String, Object> item = baseTask(name, key, status, ok, dt);
            item.put("path", path);
            item.put("exists", exists);
            item.put("successFile", successFile);
            item.put("fileCount", files);
            item.put("bytes", bytes);
            tasks.add(item);
        }
        return tasks;
    }

    private List<Map<String, Object>> buildCheckingHdfsTasks(String dt) {
        String[][] layers = {
                {"ODS 交易原始数据", "ods_trans_event"},
                {"DWD 交易明细", "dwd_trans_event"},
                {"DWS 用户日汇总", "dws_user_transaction_daily"},
                {"DWS 收款方日汇总", "dws_counterparty_daily"},
                {"DWS 设备日汇总", "dws_device_daily"},
                {"DWS 用户风险汇总", "dws_user_risk_daily"},
                {"ADS 用户画像", "ads_user_profile"},
                {"ADS 交易风险明细", "ads_transaction_risk_detail"},
                {"ADS 风险看板聚合", "ads_risk_dashboard"},
                {"ADS 跨省风险流向", "ads_cross_region_risk_flow"}
        };
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (String[] layer : layers) {
            Map<String, Object> item = baseTask(layer[0], layer[1], "CHECKING", false, dt);
            item.put("message", "正在后台检查 HDFS 产物");
            tasks.add(item);
        }
        return tasks;
    }

    private Map<String, Object> buildMysqlUserProfileTask(String dt) {
        long count = 0L;
        boolean ok = false;
        String status = "EMPTY";
        String message = null;
        try {
            Long rowCount = userProfileDao.count();
            count = rowCount == null ? 0L : rowCount;
            ok = count > 0;
            status = ok ? "SUCCESS" : "EMPTY";
        } catch (Exception e) {
            status = "ERROR";
            message = e.getMessage();
        }
        Map<String, Object> item = baseTask("MySQL user_profile", "mysql_user_profile", status, ok, dt);
        item.put("rowCount", count);
        if (message != null) {
            item.put("message", message);
        }
        return item;
    }

    private List<Map<String, Object>> buildUnknownHdfsTasks(String dt, String message) {
        String[][] layers = {
                {"ODS 交易原始数据", "ods_trans_event"},
                {"DWD 交易明细", "dwd_trans_event"},
                {"DWS 用户日汇总", "dws_user_transaction_daily"},
                {"DWS 收款方日汇总", "dws_counterparty_daily"},
                {"DWS 设备日汇总", "dws_device_daily"},
                {"DWS 用户风险汇总", "dws_user_risk_daily"},
                {"ADS 用户画像", "ads_user_profile"},
                {"ADS 交易风险明细", "ads_transaction_risk_detail"},
                {"ADS 风险看板聚合", "ads_risk_dashboard"},
                {"ADS 跨省风险流向", "ads_cross_region_risk_flow"}
        };
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (String[] layer : layers) {
            Map<String, Object> item = baseTask(layer[0], layer[1], "UNKNOWN", false, dt);
            item.put("message", message);
            tasks.add(item);
        }
        return tasks;
    }

    private Map<String, Object> baseTask(String name, String key, String status, boolean ok, String dt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("key", key);
        item.put("status", status);
        item.put("ok", ok);
        if (dt != null) {
            item.put("dt", dt);
        }
        return item;
    }

    private List<Map<String, Object>> cloneTasks(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> item : source) {
            copy.add(new LinkedHashMap<>(item));
        }
        return copy;
    }

    private String runRemote(String script) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(sshUsername, sshHost, sshPort);
        session.setPassword(sshPassword);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect((int) commandTimeoutMs);

        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("bash -lc " + shellQuote(script));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            InputStream stdout = channel.getInputStream();
            channel.setErrStream(err);
            channel.connect((int) commandTimeoutMs);

            long start = System.currentTimeMillis();
            byte[] buffer = new byte[4096];
            while (true) {
                drain(stdout, out, buffer);
                if (channel.isClosed()) {
                    break;
                }
                if (System.currentTimeMillis() - start > commandTimeoutMs) {
                    throw new RuntimeException("HDFS 状态命令超时");
                }
                Thread.sleep(100L);
            }
            drain(stdout, out, buffer);
            int exitStatus = channel.getExitStatus();
            String stdoutText = new String(out.toByteArray(), StandardCharsets.UTF_8);
            String stderrText = new String(err.toByteArray(), StandardCharsets.UTF_8);
            if (exitStatus != 0) {
                throw new RuntimeException("远程命令退出码 " + exitStatus + ": " + stderrText);
            }
            return stdoutText;
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            session.disconnect();
        }
    }

    private void drain(InputStream input, ByteArrayOutputStream output, byte[] buffer) throws Exception {
        while (input.available() > 0) {
            int len = input.read(buffer);
            if (len < 0) {
                break;
            }
            output.write(buffer, 0, len);
        }
    }

    private String hadoopEnv() {
        return "export JAVA_HOME=" + shellQuote(javaHome) + "\n"
                + "export HADOOP_HOME=" + shellQuote(hadoopHome) + "\n"
                + "export PATH=$JAVA_HOME/bin:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH\n";
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String normalizeDt(String dt) {
        if (dt == null) {
            return null;
        }
        String trimmed = dt.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }
}
