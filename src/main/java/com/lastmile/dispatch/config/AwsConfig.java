package com.lastmile.dispatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sns.SnsClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    @Value("${aws.endpoint-override:#{null}}")
    private String endpointOverride;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder().region(Region.of(region));
        if (endpointOverride != null) builder.endpointOverride(URI.create(endpointOverride));
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    }

    @Bean
    public SnsClient snsClient() {
        var builder = SnsClient.builder().region(Region.of(region));
        if (endpointOverride != null) builder.endpointOverride(URI.create(endpointOverride));
        return builder.build();
    }
}
