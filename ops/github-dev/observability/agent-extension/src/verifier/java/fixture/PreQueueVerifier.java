package fixture;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

/** 仅测试扩展：在实际 batch processor 入队前报告事件数量，不读取或输出事件正文。 */
public final class PreQueueVerifier implements AutoConfigurationCustomizerProvider {
    @Override
    public void customize(AutoConfigurationCustomizer customizer) {
        customizer.addSpanProcessorCustomizer((delegate, config) -> new SpanProcessor() {
            @Override
            public void onStart(Context parent, ReadWriteSpan span) { delegate.onStart(parent, span); }
            @Override
            public boolean isStartRequired() { return delegate.isStartRequired(); }
            @Override
            public void onEnd(ReadableSpan span) {
                System.out.println("PRIVACY_PREQUEUE_EVENTS=" + span.toSpanData().getEvents().size());
                delegate.onEnd(span);
            }
            @Override
            public boolean isEndRequired() { return true; }
            @Override
            public CompletableResultCode shutdown() { return delegate.shutdown(); }
            @Override
            public CompletableResultCode forceFlush() { return delegate.forceFlush(); }
        });
    }
}
