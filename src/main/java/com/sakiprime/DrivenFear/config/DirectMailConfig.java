package com.sakiprime.DrivenFear.config;

import com.aliyun.dm20151123.Client;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class DirectMailConfig {

    @Value("${aliyun.dm.accessKeyId}")
    private String accessKeyId;
    @Value("${aliyun.dm.accessKeySecret}")
    private String accessKeySecret;
    @Value("${aliyun.dm.endpoint}")
    private String endpoint;

    @Bean
    public Client aliyunDmClient() throws Exception {

        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = endpoint;
        return new Client(config);
    }
}
