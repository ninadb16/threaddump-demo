package no.bekk.threaddumpdemo.web;

import java.time.Duration;

public final class Utils {
    private Utils() {
    }

    public static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating slow work", e);
        }
    }
}
