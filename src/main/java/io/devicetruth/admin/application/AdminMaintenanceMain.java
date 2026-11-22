package io.devicetruth.admin.application;

import io.devicetruth.admin.api.AdminFailure;
import io.devicetruth.admin.api.AdminPages;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 可信终端入口；数据库秘密仅来自进程环境，不通过参数、HTTP 或回显传递。 */
public final class AdminMaintenanceMain {
    public static void main(String[] args) {
        try {
            if(args.length<3) throw AdminFailure.input();
            var source=new DriverManagerDataSource(required("DEVICETRUTH_MAINTENANCE_PG_URL"),required("DEVICETRUTH_MAINTENANCE_PG_USERNAME"),required("DEVICETRUTH_MAINTENANCE_PG_PASSWORD"));
            var maintenance=new AdminMaintenance(new JdbcTemplate(source),new TransactionTemplate(new DataSourceTransactionManager(source)));
            switch(args[0]) {
                case "create-admin" -> {if(args.length!=4)throw AdminFailure.input();var created=maintenance.createAdmin(args[1],args[2],args[3],readPassword());System.out.println("ADMIN_ACCOUNT_ID="+created.userId());}
                case "reset-admin-password" -> {if(args.length!=3)throw AdminFailure.input();maintenance.resetAdminPassword(args[1],AdminPages.uuid(args[2]),readPassword());}
                case "quota-pilot" -> {if(args.length!=3)throw AdminFailure.input();AdminQuotaPolicy.install(new JdbcTemplate(source),new TransactionTemplate(new DataSourceTransactionManager(source)),args[1],args[2]);}
                case "bootstrap" -> {if(args.length!=3)throw AdminFailure.input();maintenance.platform(args[1],AdminPages.uuid(args[2]),true,true,"OPS_REQUEST");}
                case "platform" -> {if(args.length!=5)throw AdminFailure.input();maintenance.platform(args[1],AdminPages.uuid(args[2]),enabled(args[3]),false,args[4]);}
                case "grant" -> {if(args.length!=7)throw AdminFailure.input();maintenance.grant(args[1],AdminPages.uuid(args[2]),AdminPages.uuid(args[3]),args[4],enabled(args[5]),args[6]);}
                default -> throw AdminFailure.input();
            }
            System.out.println("MAINTENANCE_SUCCESS");
        } catch(AdminFailure e) {System.err.println(e.code);System.exit(2);}
        catch(Exception e) {System.err.println("MAINTENANCE_UNAVAILABLE");System.exit(3);}
    }
    private static boolean enabled(String value) {if(!value.equals("enable") && !value.equals("disable"))throw AdminFailure.input();return value.equals("enable");}
    private static String readPassword() {
        var console=System.console();
        if(console==null)throw new AdminFailure(400,"INTERACTIVE_CONSOLE_REQUIRED");
        char[] first=console.readPassword("管理员密码（12–128位，输入不显示）：");
        char[] second=console.readPassword("再次输入：");
        try {
            if(first==null || second==null || !java.util.Arrays.equals(first,second))throw AdminFailure.input();
            return new String(first);
        } finally {
            if(first!=null)java.util.Arrays.fill(first,'\0');
            if(second!=null)java.util.Arrays.fill(second,'\0');
        }
    }
    private static String required(String name) {String value=System.getenv(name);if(value==null || value.isBlank())throw new AdminFailure(503,"MAINTENANCE_CONFIGURATION_REQUIRED");return value;}
}
