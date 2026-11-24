package com.kellen.config.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.TimeoutCountDown;
import org.apache.dubbo.rpc.model.ScopeModel;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 调用端重试过滤器的进程内测试辅助：测试服务接口、URL 与假提供方。
 */
final class RetryFixtures {

    static final String INTERFACE = Service.class.getName();

    private RetryFixtures() {
    }

    /**
     * 调用端 URL。
     *
     * @param scopeModel 绑定的模型（每个测试新建，不用默认模型）
     * @param keyValues  额外参数，键值交替
     * @return 调用端 URL
     */
    static URL consumerUrl(ScopeModel scopeModel, String... keyValues) {
        Map<String, String> parameters = parameters(keyValues);
        parameters.put("side", "consumer");
        parameters.put("application", "retry-test-consumer");
        return new URL("consumer", "consumer.test", 0, INTERFACE, parameters).setScopeModel(scopeModel);
    }

    /**
     * 提供方 URL。
     *
     * @param scopeModel 绑定的模型
     * @param port       区分提供方用的端口
     * @param keyValues  额外参数，键值交替
     * @return 提供方 URL
     */
    static URL providerUrl(ScopeModel scopeModel, int port, String... keyValues) {
        Map<String, String> parameters = parameters(keyValues);
        parameters.put("side", "consumer");
        parameters.put("application", "retry-test-provider");
        return new URL("tri", "provider.test", port, INTERFACE, parameters).setScopeModel(scopeModel);
    }

    /**
     * 对测试服务某个方法的一次同步调用。
     *
     * @param method 方法名
     * @return 调用对象
     */
    static RpcInvocation invocation(String method) {
        return new RpcInvocation(null, method, INTERFACE, INTERFACE, new Class<?>[] {String.class}, new Object[] {"key"});
    }

    private static Map<String, String> parameters(String... keyValues) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("interface", INTERFACE);
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            parameters.put(keyValues[index], keyValues[index + 1]);
        }
        return parameters;
    }

    /**
     * 测试服务接口。
     */
    interface Service {

        String read(String key);

        String write(String key);
    }

    /**
     * 假提供方：可设某时刻前不可用，按次序给出每次调用的结果（用完后返回成功），记下调用次数与每次看到的超时倒计时。
     */
    static final class FakeProvider implements Invoker<Service> {

        private final URL url;

        private final Deque<Function<Invocation, Result>> script = new ArrayDeque<>();

        private final AtomicInteger calls = new AtomicInteger();

        private final List<Long> countdownsSeen = Collections.synchronizedList(new ArrayList<>());

        private volatile boolean delayed;

        private volatile long availableAtNanos;

        FakeProvider(URL url) {
            this.url = url;
        }

        /**
         * @param unavailableFor 从现在起不可用多久
         * @return 本对象
         */
        FakeProvider availableAfter(Duration unavailableFor) {
            availableAtNanos = System.nanoTime() + unavailableFor.toNanos();
            delayed = true;
            return this;
        }

        /**
         * @param failures 接下来几次调用依次抛出的异常
         * @return 本对象
         */
        FakeProvider thenThrow(RpcException... failures) {
            for (RpcException failure : failures) {
                script.addLast(invocation -> {
                    throw failure;
                });
            }
            return this;
        }

        /**
         * @param result 下一次调用的结果
         * @return 本对象
         */
        FakeProvider thenReturn(Function<Invocation, Result> result) {
            script.addLast(result);
            return this;
        }

        /**
         * @return 被调用的次数
         */
        int calls() {
            return calls.get();
        }

        /**
         * @return 每次被调用时客户端附件里超时倒计时的剩余毫秒数，没有倒计时记 {@code null}
         */
        List<Long> countdownsSeen() {
            return List.copyOf(countdownsSeen);
        }

        @Override
        public Class<Service> getInterface() {
            return Service.class;
        }

        @Override
        public URL getUrl() {
            return url;
        }

        @Override
        public boolean isAvailable() {
            // nanoTime 只能比较差值，不能直接比大小。
            return !delayed || System.nanoTime() - availableAtNanos >= 0;
        }

        @Override
        public Result invoke(Invocation invocation) {
            calls.incrementAndGet();
            Object countdown = RpcContext.getClientAttachment().getObjectAttachment(CommonConstants.TIME_COUNTDOWN_KEY);
            countdownsSeen.add(countdown instanceof TimeoutCountDown remaining
                    ? remaining.timeRemaining(TimeUnit.MILLISECONDS) : null);
            Function<Invocation, Result> next;
            synchronized (script) {
                next = script.pollFirst();
            }
            if (next != null) {
                return next.apply(invocation);
            }
            return AsyncRpcResult.newDefaultAsyncResult("ok:" + invocation.getMethodName(), invocation);
        }

        @Override
        public void destroy() {
        }
    }
}
