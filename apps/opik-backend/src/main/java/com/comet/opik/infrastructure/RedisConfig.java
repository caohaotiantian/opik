package com.comet.opik.infrastructure;

import com.comet.opik.infrastructure.aws.AwsIamCredentialsResolver;
import com.comet.opik.infrastructure.redis.RedisUrl;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.dropwizard.util.Duration;
import io.dropwizard.validation.MinDuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Data
public class RedisConfig {

    @Valid @JsonProperty
    private String singleNodeUrl;

    @Valid @JsonProperty
    private AwsIamAuthConfig awsIamAuth = new AwsIamAuthConfig();

    public Config build() {
        Objects.requireNonNull(singleNodeUrl, "singleNodeUrl must not be null");
        RedisUrl redisUrl = RedisUrl.parse(singleNodeUrl);

        Config config = new Config();

        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(singleNodeUrl);

        if (awsIamAuth.isEnabled()) {
            // Configure Redis with AWS IAM authentication using DefaultCredentialsProvider
            // This will read from environment variables, system properties, IAM roles, etc.
            singleServerConfig
                    .setCredentialsResolver(new AwsIamCredentialsResolver(awsIamAuth));
        }

        singleServerConfig
                .setDatabase(redisUrl.database());

        // Use SerializationCodec instead of JsonJacksonCodec to avoid Jackson type handling issues
        // This uses Java serialization which is simpler and doesn't require @class properties
        config.setCodec(new SerializationCodec());
        return config;
    }

    /**
     * Create a simple ObjectMapper for Redis without polymorphic type handling
     */
    private static ObjectMapper createRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Basic configuration
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Register JavaTimeModule for Instant, LocalDateTime, etc.
        mapper.registerModule(new JavaTimeModule());

        // IMPORTANT: Do NOT activate default typing - this causes the @class property requirement
        // mapper.activateDefaultTyping(...) is NOT called

        return mapper;
    }

    @Data
    public static class AwsIamAuthConfig {

        @Valid @JsonProperty
        private boolean enabled = false;

        @Valid @JsonProperty
        @NotNull private String awsUserId = "";

        @Valid @JsonProperty
        @NotBlank private String awsRegion = "us-east-1";

        @Valid @JsonProperty
        @NotNull private String awsResourceName = ""; // replication group / cluster / serverless name

        // Token cache refresh/expire timings
        @Valid @JsonProperty
        @NotNull @MinDuration(value = 1, unit = TimeUnit.SECONDS)
        private Duration tokenCacheRefreshAfter = Duration.minutes(13);

        @Valid @JsonProperty
        @NotNull @MinDuration(value = 2, unit = TimeUnit.SECONDS)
        private Duration tokenCacheExpireAfter = Duration.minutes(14);

        // Presigned token expiry duration
        @Valid @JsonProperty
        @NotNull @MinDuration(value = 3, unit = TimeUnit.SECONDS)
        private Duration tokenExpiryDuration = Duration.minutes(15);
    }

}
