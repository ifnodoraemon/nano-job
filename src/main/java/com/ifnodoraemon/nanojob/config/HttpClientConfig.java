package com.ifnodoraemon.nanojob.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient jobHttpClient(NanoJobProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getHttp().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
