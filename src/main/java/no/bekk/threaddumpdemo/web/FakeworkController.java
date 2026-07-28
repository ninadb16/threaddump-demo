package no.bekk.threaddumpdemo.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fakework")
public class FakeworkController {
    private static final Logger LOG = LoggerFactory.getLogger(FakeworkController.class);
    private static final String LONG_STRING = "asdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdf";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final HttpClient httpClient;
    private final Duration scenarioDuration;
    private final Object lock = new Object();
    private final ExecutorService poolHolder = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "db-pool-connection-holder");
        thread.setDaemon(true);
        return thread;
    });

    public FakeworkController(JdbcTemplate jdbc, DataSource dataSource, HttpClient httpClient,
                              @Value("${threaddump-demo.scenario-duration:20s}") Duration scenarioDuration) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.httpClient = httpClient;
        this.scenarioDuration = scenarioDuration;
    }

    @GetMapping("database_read")
    public void databaseRead() {
        jdbc.queryForObject("VALUES APP.SLEEP_MS(?)", Integer.class, durationMillisAsInt());
    }

    @GetMapping("tcp_connect")
    public void tcpConnect() throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("203.0.113.1", 80), durationMillisAsInt());
        }
    }

    @GetMapping("http_client_get")
    public void httpClientGet() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpstat.us/200?sleep=" + durationMillisAsInt()))
                .GET()
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    @GetMapping("db_pool_get_connection")
    public void dbPoolGetConnection() throws Exception {
        CountDownLatch connectionHeld = new CountDownLatch(1);
        CountDownLatch releaseConnection = new CountDownLatch(1);
        AtomicReference<Throwable> holderFailure = new AtomicReference<>();

        poolHolder.submit(() -> holdOnlyConnection(connectionHeld, releaseConnection, holderFailure));
        if (!connectionHeld.await(5, TimeUnit.SECONDS)) {
            releaseConnection.countDown();
            throw new IllegalStateException("Timed out waiting for the pool-holder thread");
        }
        Throwable failure = holderFailure.get();
        if (failure != null) {
            throw new IllegalStateException("Pool-holder thread failed", failure);
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("VALUES 1")) {
            statement.execute();
        } finally {
            releaseConnection.countDown();
        }
    }

    private void holdOnlyConnection(CountDownLatch connectionHeld, CountDownLatch releaseConnection,
                                    AtomicReference<Throwable> holderFailure) {
        try (Connection ignored = dataSource.getConnection()) {
            connectionHeld.countDown();
            releaseConnection.await(scenarioDuration.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Throwable error) {
            holderFailure.set(error);
            connectionHeld.countDown();
            LOG.error("Connection-holder thread failed", error);
        }
    }

    @GetMapping("lock_contention")
    public void lockContention() throws InterruptedException {
        CountDownLatch lockHeld = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (lock) {
                lockHeld.countDown();
                Utils.sleep(scenarioDuration);
            }
        }, "monitor-lock-holder");
        holder.start();
        lockHeld.await();
        synchronized (lock) {
            LOG.info("Finally got lock!");
        }
    }

    @GetMapping("cpu_loop")
    public void cpuLoop() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        long deadline = System.nanoTime() + scenarioDuration.toNanos();
        byte[] input = LONG_STRING.getBytes(StandardCharsets.UTF_8);
        while (System.nanoTime() < deadline) {
            digest.digest(input);
        }
    }

    private int durationMillisAsInt() {
        return Math.toIntExact(scenarioDuration.toMillis());
    }

    @PreDestroy
    void shutdown() {
        poolHolder.shutdownNow();
    }
}
