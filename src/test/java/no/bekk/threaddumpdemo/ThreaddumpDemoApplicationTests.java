package no.bekk.threaddumpdemo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(properties = "threaddump-demo.scenario-duration=150ms")
@AutoConfigureMockMvc
class ThreaddumpDemoApplicationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired MockMvc mockMvc;
    @Autowired RequestMappingHandlerMapping mappings;

    @Test
    void contextStartsWithEmbeddedDerbyAndJdbcWorks() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getJdbcUrl()).startsWith("jdbc:derby:memory:");
        assertThat(jdbc.queryForObject("VALUES 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void derbyFunctionActuallySleepsInsideJdbc() {
        long start = System.nanoTime();
        assertThat(jdbc.queryForObject("VALUES APP.SLEEP_MS(?)", Integer.class, 150)).isEqualTo(150);
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(100);
    }

    @Test
    void databaseAndPoolEndpointsComplete() throws Exception {
        mockMvc.perform(get("/fakework/database_read")).andExpect(status().isOk());
        long start = System.nanoTime();
        mockMvc.perform(get("/fakework/db_pool_get_connection")).andExpect(status().isOk());
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(100);
    }

    @Test
    void allOriginalEndpointMappingsArePresent() {
        Set<String> paths = mappings.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPathPatternsCondition() != null)
                .flatMap(info -> info.getPathPatternsCondition().getPatterns().stream())
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertThat(paths).contains(
                "/fakework/database_read",
                "/fakework/tcp_connect",
                "/fakework/http_client_get",
                "/fakework/db_pool_get_connection",
                "/fakework/lock_contention",
                "/fakework/cpu_loop");
    }
}
