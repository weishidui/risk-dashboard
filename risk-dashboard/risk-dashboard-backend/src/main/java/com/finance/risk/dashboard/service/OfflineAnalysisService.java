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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private static final DateTimeFormatter LOG_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_TASK_LOG_LINES = 80;
    private static final String SCRIPT_BASE = "/home/master0/javacode/risk-profile-mapreduce";
    private static final String RUNNER_SCRIPT = SCRIPT_BASE + "/run_offline_30d_excluding_today.sh";
    private static final Set<String> ACTIVE_STATUSES = Collections.unmodifiableSet(
            new java.util.HashSet<>(Arrays.asList("RUNNING", "PAUSE_REQUESTED", "PAUSED", "CANCEL_REQUESTED")));
    private static final String[][] STEP_DEFINITIONS = {
            {"ods-clean", "ODS 分区准备", "清理本次交易 ODS 分区"},
            {"ods-transaction", "ODS 原始交易导入", "MySQL transaction_history 到 HDFS"},
            {"ods-dimensions", "ODS 维表同步", "登录、账户、收款方、黑名单等源表"},
            {"ods-to-dwd", "DWD 明细清洗", "标准化交易明细"},
            {"dws-user", "DWS 用户汇总", "用户交易聚合"},
            {"dws-counterparty", "DWS 收款方汇总", "收款方风险聚合"},
            {"dws-device", "DWS 设备汇总", "设备风险聚合"},
            {"dws-risk", "DWS 历史风险汇总", "历史风险种子聚合"},
            {"ads-profile", "ADS 用户画像", "生成用户画像结果"},
            {"mysql-profile", "用户画像入库", "写入 MySQL 与 Redis"},
            {"ads-risk-detail", "ADS 风险明细", "生成交易风险明细"},
            {"ads-risk-dashboard", "ADS 大屏指标", "生成趋势、排行与行为指标"},
            {"ads-cross-region-flow", "ADS 跨省流向", "生成跨区域风险流向"},
            {"ads-mysql", "ADS 结果发布", "发布离线分析结果到 MySQL"}
    };

    /**
     * 启动近30天离线分析
     */
    public Map<String, Object> startRecent30DaysAnalysis() {
        // The task date is fixed to the Shanghai calendar date. The cluster script then
        // analyzes [today - 30 days 00:00:00, today 00:00:00), excluding today itself.
        final String dt = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DT_FMT);

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
        Map<String, Object> task = new ConcurrentHashMap<>();
        task.put("dt", dt);
        task.put("status", "RUNNING");
        task.put("currentStep", "init");
        task.put("message", "正在初始化离线分析任务");
        task.put("windowStart", windowStart);
        task.put("windowEnd", windowEnd);
        task.put("startTime", System.currentTimeMillis());
        task.put("progress", 0);
        task.put("logs", new CopyOnWriteArrayList<String>());
        task.put("steps", createSteps());
        appendLog(task, "任务已创建，准备连接离线集群");
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

    public Map<String, Object> getCurrentStatus() {
        Map<String, Object> current = findActiveTask();
        if (current == null) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "NOT_FOUND");
            resp.put("message", "当前没有运行中的离线分析任务");
            return resp;
        }
        return getStatus(String.valueOf(current.get("dt")));
    }

    public boolean hasActiveTask() {
        return findActiveTask() != null;
    }

    public Map<String, Object> pauseCurrentAnalysis() {
        Map<String, Object> task = requireActiveTask();
        if (!"RUNNING".equals(task.get("status"))) {
            return taskResponse(false, task, "任务当前不可暂停");
        }
        try {
            executeControlCommand(String.valueOf(task.get("dt")), "pause");
            task.put("status", "PAUSE_REQUESTED");
            task.put("message", "已请求暂停，将在当前 MapReduce Job 完成后暂停");
            appendLog(task, "已请求暂停，等待当前 MapReduce Job 到达安全检查点");
            return taskResponse(true, task, "暂停请求已发送");
        } catch (Exception e) {
            return taskResponse(false, task, "暂停请求失败: " + e.getMessage());
        }
    }

    public Map<String, Object> resumeCurrentAnalysis() {
        Map<String, Object> task = requireActiveTask();
        if (!"PAUSED".equals(task.get("status")) && !"PAUSE_REQUESTED".equals(task.get("status"))) {
            return taskResponse(false, task, "任务当前不可继续");
        }
        try {
            executeControlCommand(String.valueOf(task.get("dt")), "resume");
            task.put("status", "RUNNING");
            task.put("message", "已继续离线分析任务");
            appendLog(task, "已继续任务，等待脚本恢复执行");
            return taskResponse(true, task, "继续请求已发送");
        } catch (Exception e) {
            return taskResponse(false, task, "继续请求失败: " + e.getMessage());
        }
    }

    public Map<String, Object> cancelCurrentAnalysis() {
        Map<String, Object> task = requireActiveTask();
        try {
            executeControlCommand(String.valueOf(task.get("dt")), "cancel");
            task.put("status", "CANCEL_REQUESTED");
            task.put("message", "已请求安全结束，将在当前 MapReduce Job 完成后结束");
            appendLog(task, "已请求安全结束，最近一次成功结果将继续保留");
            return taskResponse(true, task, "安全结束请求已发送");
        } catch (Exception e) {
            return taskResponse(false, task, "结束请求失败: " + e.getMessage());
        }
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
            appendLog(task, "任务失败: " + e.getMessage());
        }
    }

    /** 生产模式：SSH 到 master0 执行脚本 */
    private void executeOnCluster(String dt, Map<String, Object> task) throws Exception {
        if (executeWithJsch(dt, task)) {
            return;
        }
        String scriptPath = RUNNER_SCRIPT;
        String[] steps = taskStepKeys();
        String[] messages = {
            "正在从 MySQL 导入 ODS 交易数据", "正在执行 DWD 清洗",
            "正在生成用户交易汇总", "正在生成收款方汇总",
            "正在生成设备汇总", "正在汇总历史风险",
            "正在合成用户画像", "正在写入 MySQL user_profile", "正在同步 Redis 缓存"
        };

        // 构建 SSH 命令
        ProcessBuilder pb = new ProcessBuilder(
            "ssh", sshUsername + "@" + sshHost,
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
            updateClusterStep(line, steps, task);
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
            task.put("progress", 100);
            markAllSteps(task, "SUCCESS");
            appendLog(task, "离线分析完成，ADS 结果已发布");
        } else if (exitCode == 130 || "CANCELED".equals(task.get("status"))) {
            task.put("status", "CANCELED");
            task.put("message", "离线分析已安全结束，继续展示最近一次成功结果");
            markCurrentStep(task, "CANCELED");
            appendLog(task, "离线分析已安全结束");
        } else {
            task.put("status", "FAILED");
            task.put("message", "脚本执行失败，退出码: " + exitCode);
            markCurrentStep(task, "FAILED");
        }
        task.put("endTime", System.currentTimeMillis());
    }

    /** 开发模式：模拟执行过程 */
    private boolean executeWithJsch(String dt, Map<String, Object> task) throws Exception {
        String scriptPath = RUNNER_SCRIPT;
        String command = "cd " + scriptPath.substring(0, scriptPath.lastIndexOf('/'))
                + " && chmod +x " + scriptPath.substring(scriptPath.lastIndexOf('/') + 1)
                + " && ./" + scriptPath.substring(scriptPath.lastIndexOf('/') + 1) + " " + shellQuote(dt);
        String[] steps = taskStepKeys();

        JSch jsch = new JSch();
        appendLog(task, "正在连接 master0 并启动离线脚本");
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
                if (channel.getExitStatus() == 130 || "CANCELED".equals(task.get("status"))) {
                    task.put("status", "CANCELED");
                    task.put("message", "离线分析已安全结束，继续展示最近一次成功结果");
                    task.put("endTime", System.currentTimeMillis());
                    markCurrentStep(task, "CANCELED");
                    appendLog(task, "离线分析已安全结束");
                    return true;
                }
                throw new IllegalStateException("cluster script failed: " + errors.toString("UTF-8"));
            }
            task.put("status", "SUCCESS");
            task.put("message", "Offline ADS snapshot and risk analysis completed");
            task.put("endTime", System.currentTimeMillis());
            task.put("progress", 100);
            markAllSteps(task, "SUCCESS");
            appendLog(task, "离线分析完成，ADS 结果已写入 MySQL 和 Redis");
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
        appendLog(task, line);
        if (line.contains("task_state=PAUSED")) {
            task.put("status", "PAUSED");
            task.put("message", "任务已暂停，可继续或安全结束");
            markCurrentStep(task, "PAUSED");
            return;
        }
        if (line.contains("task_state=RUNNING")) {
            task.put("status", "RUNNING");
            task.put("message", "任务已继续执行");
            markCurrentStep(task, "RUNNING");
            return;
        }
        if (line.contains("task_state=CANCELED")) {
            task.put("status", "CANCELED");
            task.put("message", "任务正在安全结束");
            return;
        }
        for (int i = 0; i < steps.length; i++) {
            String step = steps[i];
            if (line.contains("step=" + step)) {
                markStep(task, step);
                task.put("message", "正在执行: " + step);
                appendLog(task, "进入阶段: " + step);
                return;
            }
        }
    }

    private List<Map<String, Object>> createSteps() {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (String[] definition : STEP_DEFINITIONS) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("key", definition[0]);
            step.put("name", definition[1]);
            step.put("detail", definition[2]);
            step.put("status", "PENDING");
            steps.add(step);
        }
        return steps;
    }

    private String[] taskStepKeys() {
        String[] keys = new String[STEP_DEFINITIONS.length];
        for (int i = 0; i < STEP_DEFINITIONS.length; i++) {
            keys[i] = STEP_DEFINITIONS[i][0];
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private void markStep(Map<String, Object> task, String key) {
        Object rawSteps = task.get("steps");
        if (!(rawSteps instanceof List)) {
            return;
        }
        List<Map<String, Object>> steps = (List<Map<String, Object>>) rawSteps;
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            if (key.equals(step.get("key"))) {
                for (int previous = 0; previous < i; previous++) {
                    String previousStatus = String.valueOf(steps.get(previous).get("status"));
                    if (!"SUCCESS".equals(previousStatus) && !"FAILED".equals(previousStatus)
                            && !"CANCELED".equals(previousStatus)) {
                        steps.get(previous).put("status", "SUCCESS");
                    }
                }
                step.put("status", "RUNNING");
                task.put("currentStep", key);
                task.put("progress", Math.min(95, 5 + ((i + 1) * 90 / steps.size())));
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void markCurrentStep(Map<String, Object> task, String status) {
        Object rawSteps = task.get("steps");
        if (!(rawSteps instanceof List)) {
            return;
        }
        Object currentStep = task.get("currentStep");
        if (currentStep == null) {
            return;
        }
        for (Map<String, Object> step : (List<Map<String, Object>>) rawSteps) {
            if (currentStep.equals(step.get("key"))) {
                step.put("status", status);
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void markAllSteps(Map<String, Object> task, String status) {
        Object rawSteps = task.get("steps");
        if (!(rawSteps instanceof List)) {
            return;
        }
        for (Map<String, Object> step : (List<Map<String, Object>>) rawSteps) {
            step.put("status", status);
        }
    }

    private Map<String, Object> findActiveTask() {
        Map<String, Object> current = null;
        long latestStart = Long.MIN_VALUE;
        for (Map<String, Object> task : taskStore.values()) {
            if (ACTIVE_STATUSES.contains(String.valueOf(task.get("status")))) {
                long startTime = ((Number) task.get("startTime")).longValue();
                if (startTime > latestStart) {
                    current = task;
                    latestStart = startTime;
                }
            }
        }
        return current;
    }

    private Map<String, Object> requireActiveTask() {
        Map<String, Object> task = findActiveTask();
        if (task == null) {
            throw new IllegalStateException("当前没有可控制的离线分析任务");
        }
        return task;
    }

    private Map<String, Object> taskResponse(boolean success, Map<String, Object> task, String message) {
        Map<String, Object> response = new LinkedHashMap<>(task);
        response.put("success", success);
        response.put("message", message);
        return response;
    }

    private void executeControlCommand(String dt, String action) throws Exception {
        String controlPath = SCRIPT_BASE + "/logs/offline_" + dt;
        String command;
        if ("pause".equals(action)) {
            command = "mkdir -p " + SCRIPT_BASE + "/logs && touch " + controlPath + ".pause";
        } else if ("resume".equals(action)) {
            command = "rm -f " + controlPath + ".pause";
        } else if ("cancel".equals(action)) {
            command = "rm -f " + controlPath + ".pause && touch " + controlPath + ".cancel";
        } else {
            throw new IllegalArgumentException("Unsupported task control action: " + action);
        }

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
            channel.connect(15_000);
            while (!channel.isClosed()) {
                Thread.sleep(50L);
            }
            if (channel.getExitStatus() != 0) {
                throw new IllegalStateException("cluster control command failed");
            }
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            session.disconnect();
        }
    }

    @SuppressWarnings("unchecked")
    private void appendLog(Map<String, Object> task, String message) {
        Object rawLogs = task.get("logs");
        if (!(rawLogs instanceof List)) {
            return;
        }
        List<String> logs = (List<String>) rawLogs;
        String line = message == null ? "" : message.trim();
        if (line.length() > 500) {
            line = line.substring(0, 500) + " ...";
        }
        while (logs.size() >= MAX_TASK_LOG_LINES) {
            logs.remove(0);
        }
        logs.add(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(LOG_TIME_FMT) + "  " + line);
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
            task.put("progress", Math.min(95, 5 + ((i + 1) * 90 / steps.length)));
            appendLog(task, "进入阶段: " + steps[i] + " - " + messages[i]);
            log.info("[离线分析-模拟] {}/{} {}: {}", i + 1, steps.length, steps[i], messages[i]);
        }

        task.put("status", "SUCCESS");
        task.put("message", "离线分析完成（开发模式模拟），画像已更新至 MySQL 和 Redis");
        task.put("endTime", System.currentTimeMillis());
        task.put("progress", 100);
        appendLog(task, "离线分析完成，ADS 结果已写入 MySQL 和 Redis");
        log.info("[离线分析-模拟] 完成，{} 个步骤全部执行成功", steps.length);
    }
}
