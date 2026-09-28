import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线探测本机 MySQL root 口令（仅用于启动冒烟；失败则输出候选错误）。
 * 用法: java -cp <mysql-connector-j.jar>;. DbProbe
 */
public class DbProbe {

    public static void main(String[] args) {
        List<String> candidates = new ArrayList<>();
        candidates.add("");
        candidates.add("root");
        candidates.add("123456");
        candidates.add("password");
        candidates.add("mysql");
        candidates.add("12345678");
        candidates.add("greendeer");
        String user = args.length > 0 ? args[0] : "root";
        boolean found = false;
        for (String pwd : candidates) {
            String url = "jdbc:mysql://127.0.0.1:3306/?connectTimeout=2500&socketTimeout=3000&useSSL=false";
            try (Connection c = DriverManager.getConnection(url, user, pwd)) {
                System.out.println("SUCCESS user=" + user + " password=\"" + pwd + "\" server="
                        + c.getMetaData().getDatabaseProductVersion());
                found = true;
                break;
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                System.out.println("try user=" + user + " pwd=\"" + pwd + "\" -> " + msg);
            }
        }
        if (!found) {
            System.exit(2);
        }
    }
}
