package no.bekk.threaddumpdemo.db;

import java.sql.SQLException;

public final class DerbyFunctions {
    private DerbyFunctions() {
    }

    public static int sleepMs(int milliseconds) throws SQLException {
        try {
            Thread.sleep(milliseconds);
            return milliseconds;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while simulating slow Derby work", e);
        }
    }
}
