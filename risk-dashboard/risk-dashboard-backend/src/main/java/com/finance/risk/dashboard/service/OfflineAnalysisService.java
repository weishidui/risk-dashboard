package com.finance.risk.dashboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 离线分析任务服务 — 触发生成、状态追踪
 */
@Service
public class OfflineAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(OfflineAnalysisService.class);

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${risk.offline.hdfs.ssh.host:192.168.125.100}")
    private String sshHost;

    @Value("${risk.offline.hdfs.ssh.port:22}")
    private int sshPort;

    @Value("${risk.offline.hdfs.ssh.username:master0}")
    private String sshUsername;

    @Value("${risk.offline.hdfs.ssh.password:123456}")
    private String sshPassword;

    /** 任务状态缓存 */
    private final Map<String, Map<String, Object>> taskStore = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 启动近30天离线分析
     */
    public Map<String, Object> startRecent30DaysAnalysis(String dtParam) {
        final String dt = (dtParam == null || dtParam.isEmpty())
                ? LocalDate.now().format(DT_FMT) : dtParam;

        // 检查是否已有运行中的任务
        Map<String, Object> existing = taskStore.get(dt);
        if (existing != null && "RUNNING".equals(existing.get("status"))) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("message", "已有运行中的分析任务: dt=" + dt);
            resp.put("dt", dt);
            return resp;
        }

        // 计算窗口
        LocalDate endDate = LocalDate.parse(dt, DT_FMT);
        LocalDate startDate = endDate.minusDays(30);
        String windowStart = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 00:00:00";
        String windowEnd = endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 00:00:00";

        // 初始化任务状态
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("dt", dt);
        task.put("status", "RUNNING");
        task.put("currentStep", "init");
        task.put("message", "正在初始化离线分析任务");
        task.put("windowStart", windowStart);
        task.put("windowEnd", windowEnd);
        task.put("startTime", System.currentTimeMillis());
        taskStore.put(dt, task);

        // 异步执行（开发模式模拟，生产模式 SSH 执行）
        new Thread(() -> executeAnalysis(dt, windowStart, windowEnd)).start();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "近30天离线分析已启动");
        resp.put("dt", dt);
        resp.put("windowStart", windowStart);
        resp.put("windowEnd", windowEnd);
        return resp;
    }

    /**
     * 查询任务状态
     */
    public Map<String, Object> getStatus(String dt) {
        Map<String, Object> task = taskStore.get(dt);
        if (task == null) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("dt", dt);
            resp.put("status", "NOT_FOUND");
            resp.put("message", "未找到该日期的分析任务");
            return resp;
        }
        Map<String, Object> resp = new LinkedHashMap<>(task);
        resp.put("elapsed", System.currentTimeMillis() - (long) task.get("startTime"));
        return resp;
    }

    /**
     * 实际执行分析（异步）
     */
    private void executeAnalysis(String dt, String windowStart, String windowEnd) {
        Map<String, Object> task = taskStore.get(dt);
        try {
            if ("prod".equals(activeProfile)) {
                executeOnCluster(dt, task);
            } else {
                simulateExecution(dt, task);
            }
        } catch (Exception e) {
            log.error("离线分析执行失败: dt={}, error={}", dt, e.getMessage());
            task.put("status", "FAILED");
            task.put("message", "执行失败: " + e.getMessage());
            task.put("endTime", System.currentTimeMillis());
        }
    }

    /** 生产模式：SSH 到 master0 执行脚本 */
    private void executeOnCluster(String dt, Map<String, Object> task) throws Exception {
        if (executeWithJsch(dt, task)) {
            return;
        }
        String scriptPath = "/home/master0/javacode/risk-profile-mapreduce/run_offline_30d_excluding_today.sh";
        String[] steps = {
            "ods-sqoop-import", "dwd-clean", "dws-user", "dws-counterparty",
            "dws-device", "dws-risk", "ads-profile", "mysql-write", "redis-sync"
        };
        String[] messages = {
            "正在从 MySQL 导入 ODS 交易数据", "正在执行 DWD 清洗",
            "正在生成用户交易汇总", "正在生成收款方汇总",
            "正在生成设备汇总", "正在汇总历史风险",
            "正在合成用户画像", "正在写入 MySQL user_profile", "正在同步 Redis 缓存"
        };

        // 构建 SSH 命令
        ProcessBuilder pb = new ProcessBuilder(
            "ssh", "master0@192.168.154.100",
            "cd " + scriptPath.substring(0, scriptPath.lastIndexOf('/'))
            + " && chmod +x " + scriptPath.substring(scriptPath.lastIndexOf('/') + 1)
            + " && ./" + scriptPath.substring(scriptPath.lastIndexOf('/') + 1) + " " + dt
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 按脚本输出行更新步骤
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getInputStream()));
        String line;
        int stepIdx = 0;
        while ((line = reader.readLine()) != null) {
            log.info("[离线分析] {}", line);
            // 根据输出关键字更新步骤
            for (int i = stepIdx; i < steps.length; i++) {
                if (line.contains(steps[i]) || line.contains("step=" + steps[i])) {
                    stepIdx = i;
                    task.put("currentStep", steps[i]);
                    task.put("message", messages[i]);
                    break;
                }
            }
        }
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            task.put("status", "SUCCESS");
            task.put("message", "离线分析完成，画像已更新至 MySQL 和 Redis");
        } else {
            task.put("status", "FAILED");
            task.put("message", "脚本执行失败，退出码: " + exitCode);
        }
        task.put("endTime", System.currentTimeMillis());
    }

    /** 开发模式：模拟执行过程 */
    private boolean executeWithJsch(String dt, Map<String, Object> task) throws Exception {
        String scriptPath = "/home/master0/javacode/risk-profile-mapreduce/run_offline_30d_excluding_today.sh";
        String command = "cd " + scriptPath.substring(0, scriptPath.lastIndexOf('/'))
                + " && chmod +x " + scriptPath.substring(scriptPath.lastIndexOf('/') + 1)
                + " && ./" + scriptPath.substring(scriptPath.lastIndexOf('/') + 1) + " " + shellQuote(dt);
        String[] steps = {"ods-to-dwd", "dws-user", "dws-counterparty", "dws-device", "dws-risk",
                "ads-profile", "mysql-profile", "ads-risk-detail", "ads-risk-dashboard", "ads-cross-region-flow", "ads-mysql"};

        JSch jsch = new JSch();
        Session session = jsch.getSession(sshUsername, sshHost, sshPort);
        session.setPassword(sshPassword);
        Properties options = new Properties();
        options.put("StrictHostKeyChecking", "no");
        session.setConfig(options);
        session.connect(15_000);
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("bash -lc " + shellQuote(command));
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            channel.setErrStream(errors);
            InputStream stdout = channel.getInputStream();
            channel.connect(15_000);
            byte[] buffer = new byte[4096];
            StringBuilder pending = new StringBuilder();
            while (true) {
                while (stdout.available() > 0) {
                    int length = stdout.read(buffer);
                    if (length > 0) {
                        pending.append(new String(buffer, 0, length, StandardCharsets.UTF_8));
                        int newline;
                        while ((newline = pending.indexOf("\n")) >= 0) {
                            updateClusterStep(pending.substring(0, newline), steps, task);
                            pending.delete(0, newline + 1);
                        }
                    }
                }
                if (channel.isClosed()) {
                    break;
                }
                Thread.sleep(100L);
            }
            if (pending.length() > 0) {
                updateClusterStep(pending.toString(), steps, task);
            }
            if (channel.getExitStatus() != 0) {
                throw new IllegalStateException("cluster script failed: " + errors.toString("UTF-8"));
            }
            task.put("status", "SUCCESS");
            task.put("message", "Offline ADS snapshot and risk analysis completed");
            task.put("endTime", System.currentTimeMillis());
            return true;
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            session.disconnect();
        }
    }

    private void updateClusterStep(String line, String[] steps, Map<String, Object> task) {
        log.info("[offline-analysis] {}", line);
        for (String step : steps) {
            if (line.contains("step=" + step)) {
                task.put("currentStep", step);
                task.put("message", "Running " + step);
                return;
            }
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void simulateExecution(String dt, Map<String, Object> task) throws InterruptedException {
        String[] steps = {
            "ods-sqoop-import", "dwd-clean", "dws-user", "dws-counterparty",
            "dws-device", "dws-risk", "ads-profile", "mysql-write", "redis-sync"
        };
        String[] messages = {
            "正在从 MySQL 导入 ODS 交易数据", "正在执行 DWD 清洗",
            "正在生成用户交易汇总", "正在生成收款方汇总",
            "正在生成设备汇总", "正在汇总历史风险",
            "正在合成用户画像", "正在写入 MySQL user_profile", "正在同步 Redis 缓存"
        };

        for (int i = 0; i < steps.length; i++) {
            Thread.sleep(1500); // 模拟每步耗时
            task.put("currentStep", steps[i]);
            task.put("message", messages[i]);
            log.info("[离线分析-模拟] {}/{} {}: {}", i + 1, steps.length, steps[i], messages[i]);
        }

        task.put("status", "SUCCESS");
        task.put("message", "离线分析完成（开发模式模拟），画像已更新至 MySQL 和 Redis");
        task.put("endTime", System.currentTimeMillis());
        log.info("[离线分析-模拟] 完成，{} 个步骤全部执行成功", steps.length);
    }
}
