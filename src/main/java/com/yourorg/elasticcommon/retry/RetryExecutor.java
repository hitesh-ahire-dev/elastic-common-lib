package com.yourorg.elasticcommon.retry;

import com.yourorg.elasticcommon.config.EsProperties;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.function.Supplier;

public class RetryExecutor {

    private final Retry retry;

    public RetryExecutor(EsProperties esProperties) {
        this.retry = Retry.of("esRetry", RetryConfig.custom()
                .maxAttempts(esProperties.getRetry().getMaxAttempts())
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(esProperties.getRetry().getBackoffMs(), 2))
                .build());
    }

    public <T> T execute(Supplier<T> action) {
        return Retry.decorateSupplier(retry, action).get();
    }

    public void execute(Runnable action) {
        Retry.decorateRunnable(retry, action).run();
    }
}
