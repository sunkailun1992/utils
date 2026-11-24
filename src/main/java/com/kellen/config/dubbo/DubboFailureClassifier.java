package com.kellen.config.dubbo;

import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.TimeoutException;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.StatusRpcException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 把一次 Dubbo 调用失败归到 {@link DubboFailureCategory}。
 *
 * <p>Dubbo 3.3.7 的同步调用会把 Triple 状态异常包成未知错误（{@code AbstractInvoker#waitForResultIfSync}），失败转移又会重新包一层，
 * 只留错误码与原因链。所以先看最外层的错误码，再沿原因链找 Triple 状态与 Dubbo 远程异常。只收录能确定是连接或传输层的失败，
 * 判定不了的一律归为其它，不重试。</p>
 *
 * @author 孙凯伦
 */
final class DubboFailureClassifier {

    /**
     * 沿原因链最多查看的层数（含最外层）。同步调用的链通常不超过 4 层。
     */
    private static final int MAX_CAUSE_DEPTH = 8;

    private DubboFailureClassifier() {
    }

    /**
     * 判定一次尝试的失败类别。
     *
     * @param failure         这次尝试抛出的异常
     * @param markedNotSent   协议层过滤器是否标了“请求未发出”
     * @param awaitingRecheck 目录里是否有提供方正暂时被移出可用列表、等待复查
     * @return 重试类别
     */
    static DubboFailureCategory classify(RpcException failure, boolean markedNotSent, boolean awaitingRecheck) {
        if (markedNotSent) {
            return DubboFailureCategory.UNPROCESSED;
        }
        int code = failure.getCode();
        if (code == RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER) {
            return awaitingRecheck ? DubboFailureCategory.UNPROCESSED : DubboFailureCategory.NO_WAITABLE_PROVIDER;
        }
        if (isTimeout(code)) {
            return DubboFailureCategory.TIMEOUT;
        }
        if (!isUnspecific(code)) {
            return DubboFailureCategory.OTHER;
        }
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        for (int depth = 0; depth < MAX_CAUSE_DEPTH && current != null && seen.add(current); depth++) {
            DubboFailureCategory category = categoryOf(current);
            if (category != null) {
                return category;
            }
            current = current.getCause();
        }
        return DubboFailureCategory.OTHER;
    }

    /**
     * 判定原因链上的一层。
     *
     * @param failure 原因链上的一层
     * @return 这一层能判定时的类别；判定不了返回 {@code null}，接着看它的原因
     */
    private static DubboFailureCategory categoryOf(Throwable failure) {
        // remoting 的 TimeoutException 是 RemotingException 的子类，要先判。
        if (failure instanceof TimeoutException) {
            return DubboFailureCategory.TIMEOUT;
        }
        if (failure instanceof StatusRpcException status) {
            return switch (status.getStatus().code) {
                case DEADLINE_EXCEEDED -> DubboFailureCategory.TIMEOUT;
                case UNAVAILABLE, CANCELLED -> DubboFailureCategory.SENT_TRANSIENT;
                default -> DubboFailureCategory.OTHER;
            };
        }
        if (failure instanceof RemotingException) {
            return DubboFailureCategory.SENT_TRANSIENT;
        }
        if (failure instanceof RpcException rpcFailure && !isUnspecific(rpcFailure.getCode())) {
            return isTimeout(rpcFailure.getCode()) ? DubboFailureCategory.TIMEOUT : DubboFailureCategory.OTHER;
        }
        return null;
    }

    private static boolean isTimeout(int code) {
        return code == RpcException.TIMEOUT_EXCEPTION || code == RpcException.TIMEOUT_TERMINATE;
    }

    /**
     * 未知错误（0）与网络错误（1）本身说明不了失败在哪一层，要看原因链。
     */
    private static boolean isUnspecific(int code) {
        return code == RpcException.UNKNOWN_EXCEPTION || code == RpcException.NETWORK_EXCEPTION;
    }
}
