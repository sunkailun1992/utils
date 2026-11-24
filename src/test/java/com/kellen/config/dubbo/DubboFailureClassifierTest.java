package com.kellen.config.dubbo;

import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.TimeoutException;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.TriRpcStatus;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

import static org.apache.dubbo.rpc.RpcException.NETWORK_EXCEPTION;
import static org.apache.dubbo.rpc.RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER;
import static org.apache.dubbo.rpc.RpcException.TIMEOUT_EXCEPTION;
import static org.apache.dubbo.rpc.RpcException.TIMEOUT_TERMINATE;
import static org.apache.dubbo.rpc.RpcException.UNKNOWN_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设计分类表逐行验证：是否标了“未发出”、最外层错误码，以及原因链里的 Triple 状态与 Dubbo 远程异常决定类别。
 */
class DubboFailureClassifierTest {

    @Test
    void markedNotSentIsUnprocessed() {
        assertThat(classify(new RpcException(NETWORK_EXCEPTION, "x"), true, false))
                .isEqualTo(DubboFailureCategory.UNPROCESSED);
    }

    @Test
    void noProviderWhileOneAwaitsRecheckIsUnprocessed() {
        assertThat(classify(new RpcException(NO_INVOKER_AVAILABLE_AFTER_FILTER, "x"), false, true))
                .isEqualTo(DubboFailureCategory.UNPROCESSED);
    }

    @Test
    void noProviderWithNothingToAwaitFailsFast() {
        assertThat(classify(new RpcException(NO_INVOKER_AVAILABLE_AFTER_FILTER, "x"), false, false))
                .isEqualTo(DubboFailureCategory.NO_WAITABLE_PROVIDER);
    }

    @Test
    void tripleUnavailableOrCancelledIsSentTransient() {
        assertThat(classify(wrap(TriRpcStatus.UNAVAILABLE.asException()))).isEqualTo(DubboFailureCategory.SENT_TRANSIENT);
        assertThat(classify(wrap(TriRpcStatus.CANCELLED.asException()))).isEqualTo(DubboFailureCategory.SENT_TRANSIENT);
        // 未经同步等待包装时，最外层就是 Triple 状态异常本身。
        assertThat(classify(TriRpcStatus.UNAVAILABLE.asException())).isEqualTo(DubboFailureCategory.SENT_TRANSIENT);
        assertThat(classify(TriRpcStatus.CANCELLED.asException())).isEqualTo(DubboFailureCategory.SENT_TRANSIENT);
    }

    @Test
    void remotingFailureIsSentTransient() {
        RpcException failure = new RpcException(NETWORK_EXCEPTION, "x",
                new RemotingException((InetSocketAddress) null, null, "closed"));

        assertThat(classify(failure)).isEqualTo(DubboFailureCategory.SENT_TRANSIENT);
    }

    @Test
    void timeoutsAreNeverRetried() {
        assertThat(classify(new RpcException(TIMEOUT_EXCEPTION, "x"))).isEqualTo(DubboFailureCategory.TIMEOUT);
        assertThat(classify(new RpcException(TIMEOUT_TERMINATE, "x"))).isEqualTo(DubboFailureCategory.TIMEOUT);
        assertThat(classify(wrap(TriRpcStatus.DEADLINE_EXCEEDED.asException()))).isEqualTo(DubboFailureCategory.TIMEOUT);
        assertThat(classify(new RpcException(TIMEOUT_EXCEPTION, "x", TriRpcStatus.UNAVAILABLE.asException())))
                .as("最外层是超时时，原因链里的 UNAVAILABLE 不改变类别")
                .isEqualTo(DubboFailureCategory.TIMEOUT);
        assertThat(classify(wrap(new TimeoutException(false, (InetSocketAddress) null, null, "waiting"))))
                .isEqualTo(DubboFailureCategory.TIMEOUT);
    }

    @Test
    void explicitCodesAreOther() {
        for (int code : new int[] {3, 4, 5, 7, 9, 10, 11, 12, 13}) {
            assertThat(classify(new RpcException(code, "x"))).as("错误码 %d", code).isEqualTo(DubboFailureCategory.OTHER);
        }
    }

    @Test
    void noTransportEvidenceIsOther() {
        assertThat(classify(new RpcException(NETWORK_EXCEPTION, "x"))).isEqualTo(DubboFailureCategory.OTHER);
        assertThat(classify(wrap(TriRpcStatus.INTERNAL.asException()))).isEqualTo(DubboFailureCategory.OTHER);
        assertThat(classify(wrap(new IllegalStateException("x")))).isEqualTo(DubboFailureCategory.OTHER);
    }

    @Test
    void cyclicOrDeepCauseChainsTerminate() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);
        assertThat(classify(new RpcException(UNKNOWN_EXCEPTION, "x", first))).isEqualTo(DubboFailureCategory.OTHER);

        // UNAVAILABLE 放在第 21 层：分类器只看前 8 层，所以找不到它。
        Throwable deep = TriRpcStatus.UNAVAILABLE.asException();
        for (int level = 0; level < 20; level++) {
            deep = new RuntimeException("level " + level, deep);
        }
        assertThat(classify(new RpcException(UNKNOWN_EXCEPTION, "x", deep))).isEqualTo(DubboFailureCategory.OTHER);
    }

    private static RpcException wrap(Throwable failure) {
        return new RpcException(UNKNOWN_EXCEPTION, "wrapped", new ExecutionException(failure));
    }

    private static DubboFailureCategory classify(RpcException failure) {
        return classify(failure, false, false);
    }

    private static DubboFailureCategory classify(RpcException failure, boolean markedNotSent, boolean awaitingRecheck) {
        return DubboFailureClassifier.classify(failure, markedNotSent, awaitingRecheck);
    }
}
