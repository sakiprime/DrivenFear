package com.sakiprime.DrivenFear.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${dmx.api.base-url:https://www.dmxapi.cn}")
    private String dmxBaseUrl;

    @Value("${dmx.api.api-key}")
    private String dmxApiKey;

    @Bean
    public WebClient dmxWebClient() {
        return WebClient.builder()
                .baseUrl(dmxBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Authorization", dmxApiKey)
                .build();
    }
}
