package no.bekk.threaddumpdemo.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {
    @Bean
    HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }
}
