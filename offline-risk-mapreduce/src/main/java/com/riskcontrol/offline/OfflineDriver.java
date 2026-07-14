package com.riskcontrol.offline;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class OfflineDriver {
    private interface ToolFactory {
        Tool create();
    }

    private static final Map<String, ToolFactory> COMMANDS = new LinkedHashMap<String, ToolFactory>();

    static {
        COMMANDS.put("ods-to-dwd", new ToolFactory() {
            @Override
            public Tool create() {
                return new OdsToDwdJob();
            }
        });
        COMMANDS.put("mysql-to-ods", new ToolFactory() {
            @Override
            public Tool create() {
                return new MysqlToOdsJob();
            }
        });
        COMMANDS.put("mysql-table-to-ods", new ToolFactory() {
            @Override
            public Tool create() {
                return new MysqlTableToOdsJob();
            }
        });
        COMMANDS.put("dwd-to-dws-user", new ToolFactory() {
            @Override
            public Tool create() {
                return new DwdToDwsUserJob();
            }
        });
        COMMANDS.put("dwd-to-dws-cpty", new ToolFactory() {
            @Override
            public Tool create() {
                return new DwdToDwsCounterpartyJob();
            }
        });
        COMMANDS.put("dwd-to-dws-device", new ToolFactory() {
            @Override
            public Tool create() {
                return new DwdToDwsDeviceJob();
            }
        });
        COMMANDS.put("dws-to-user-profile", new ToolFactory() {
            @Override
            public Tool create() {
                return new DwsToUserProfileJob();
            }
        });
        COMMANDS.put("ads-user-profile-to-mysql", new ToolFactory() {
            @Override
            public Tool create() {
                return new AdsUserProfileToMysqlJob();
            }
        });
        COMMANDS.put("history-risk-to-dws-user-risk", new ToolFactory() {
            @Override
            public Tool create() {
                return new HistoryRiskToDwsUserRiskJob();
            }
        });
        COMMANDS.put("ads-transaction-risk-detail", new ToolFactory() {
            @Override
            public Tool create() {
                return new AdsTransactionRiskDetailJob();
            }
        });
        COMMANDS.put("ads-risk-dashboard", new ToolFactory() {
            @Override
            public Tool create() {
                return new AdsRiskDashboardJob();
            }
        });
        COMMANDS.put("ads-risk-to-mysql", new ToolFactory() {
            @Override
            public Tool create() {
                return new AdsRiskToMysqlJob();
            }
        });
        COMMANDS.put("ads-cross-region-flow", new ToolFactory() {
            @Override
            public Tool create() {
                return new AdsCrossRegionRiskFlowJob();
            }
        });
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !COMMANDS.containsKey(args[0])) {
            printUsage();
            System.exit(2);
        }
        String command = args[0];
        String[] toolArgs = Arrays.copyOfRange(args, 1, args.length);
        int exitCode = ToolRunner.run(new Configuration(), COMMANDS.get(command).create(), toolArgs);
        System.exit(exitCode);
    }

    private static void printUsage() {
        System.err.println("Usage: hadoop jar risk-profile-mapreduce-1.0.0.jar <command> [args]");
        System.err.println("Commands:");
        System.err.println("  mysql-to-ods <dt> <ods_output_dir>");
        System.err.println("  mysql-table-to-ods <dt> <table_name> <ods_output_dir>");
        System.err.println("  ods-to-dwd <ods_input_path> <dwd_output_path>");
        System.err.println("  dwd-to-dws-user <dwd_input_path> <dws_user_output_path>");
        System.err.println("  dwd-to-dws-cpty <dwd_input_path> <dws_counterparty_output_path>");
        System.err.println("  dwd-to-dws-device <dwd_input_path> <dws_device_output_path>");
        System.err.println("  history-risk-to-dws-user-risk <transaction_ods_path> <history_risk_seed_ods_path> <user_risk_output_path>");
        System.err.println("  dws-to-user-profile <dws_user_input_path...> <profile_output_path>");
        System.err.println("  ads-user-profile-to-mysql <ads_user_profile_hdfs_path>");
        System.err.println("  ads-transaction-risk-detail <dwd_transaction_path> <risk_seed_ods_path> <ads_detail_output_path>");
        System.err.println("  ads-risk-dashboard <ads_detail_input_path> <ads_dashboard_output_path>");
        System.err.println("  ads-risk-to-mysql <ads_detail_hdfs_path> <ads_dashboard_hdfs_path> [ads_cross_region_flow_hdfs_path]");
        System.err.println("  ads-cross-region-flow <ads_detail_input_path> <ads_cross_region_flow_output_path>");
    }
}
