package no.bekk.threaddumpdemo.db;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DerbyInitializer implements InitializingBean {
    private final JdbcTemplate jdbcTemplate;

    public DerbyInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void afterPropertiesSet() {
        Integer functionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM SYS.SYSALIASES A
                JOIN SYS.SYSSCHEMAS S ON A.SCHEMAID = S.SCHEMAID
                WHERE S.SCHEMANAME = 'APP'
                  AND A.ALIAS = 'SLEEP_MS'
                  AND A.ALIASTYPE = 'F'
                """, Integer.class);

        if (functionCount != null && functionCount == 0) {
            jdbcTemplate.execute("""
                    CREATE FUNCTION APP.SLEEP_MS(MILLISECONDS INTEGER)
                    RETURNS INTEGER
                    PARAMETER STYLE JAVA
                    NO SQL
                    LANGUAGE JAVA
                    EXTERNAL NAME 'no.bekk.threaddumpdemo.db.DerbyFunctions.sleepMs'
                    """);
        }

        jdbcTemplate.queryForObject("VALUES 1", Integer.class);
    }
}
