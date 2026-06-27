package com.yourorg.elasticcommon.retry;

import com.yourorg.elasticcommon.config.EsProperties;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.function.Supplier;

public class RetryExecutor {

    private static final String RETRY_NAME         = "esRetry";
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private final Retry retry;

    public RetryExecutor(EsProperties esProperties) {
        this.retry = Retry.of(RETRY_NAME, RetryConfig.custom()
                .maxAttempts(esProperties.getRetry().getMaxAttempts())
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(esProperties.getRetry().getBackoffMs(), BACKOFF_MULTIPLIER))
                .build());
    }

    public <T> T execute(Supplier<T> action) {
        return Retry.decorateSupplier(retry, action).get();
    }

    public void execute(Runnable action) {
        Retry.decorateRunnable(retry, action).run();
    }
}
