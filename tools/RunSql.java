import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

/**
 * Runs one plain SQL script (statements end with a semicolon at the end of a line, comment lines start with --) in ONE
 * transaction against the database in src/main/resources/application.properties, then commits; any error rolls everything back.
 * Credentials are read from the properties file and never printed.
 * Usage: java -cp ojdbc11.jar tools/RunSql.java db/some_script.sql
 */
public class RunSql {
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
            p.load(in);
        }
        String text = Files.readString(Path.of(args[0])).replaceAll("(?m)^--.*$", "");
        Class.forName("oracle.jdbc.OracleDriver");
        try (Connection c = DriverManager.getConnection(p.getProperty("spring.datasource.url"),
                p.getProperty("spring.datasource.username"), p.getProperty("spring.datasource.password"))) {
            c.setAutoCommit(false);
            int n = 0;
            try (Statement s = c.createStatement()) {
                for (String sql : text.split(";\\s*\\n")) {
                    String stmt = sql.trim();
                    if (stmt.isEmpty() || stmt.equalsIgnoreCase("COMMIT")) continue;
                    n += s.executeUpdate(stmt);
                }
                c.commit();
                System.out.println("committed, rows affected: " + n);
            } catch (Exception e) {
                c.rollback();
                System.out.println("ROLLED BACK: " + e.getMessage());
                throw e;
            }
        }
    }
}
