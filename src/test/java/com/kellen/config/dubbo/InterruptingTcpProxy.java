package com.kellen.config.dubbo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用 TCP 代理：在 Dubbo 调用端与 Triple 提供方之间原样转发字节，{@link #resetAll()} 模拟中间设备把连接断掉——
 * 两端立即复位（SO_LINGER 设 0 后关闭，对端收到 RST），调用端随即知道连接没了，之后的新连接照常转发。
 *
 * <p>监听调用方给定的本机地址（Dubbo 连本机时用的地址），只转发给本机回环地址上的提供方，测试结束关闭。</p>
 */
public final class InterruptingTcpProxy implements AutoCloseable {

    private final ServerSocket server;

    private final InetSocketAddress target;

    private final ExecutorService threads = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "interrupting-tcp-proxy");
        thread.setDaemon(true);
        return thread;
    });

    private final Set<Pair> pairs = ConcurrentHashMap.newKeySet();

    private final AtomicInteger accepted = new AtomicInteger();

    private InterruptingTcpProxy(InetAddress listenAddress, int targetPort) throws IOException {
        this.server = new ServerSocket(0, 50, listenAddress);
        this.target = new InetSocketAddress(InetAddress.getLoopbackAddress(), targetPort);
        threads.execute(this::acceptLoop);
    }

    /**
     * 在给定本机地址的空闲端口上启动代理。
     *
     * @param listenAddress 监听地址（本机地址）
     * @param targetPort    提供方在本机回环地址上的端口
     * @return 已开始接受连接的代理
     * @throws IOException 端口打不开
     */
    public static InterruptingTcpProxy start(InetAddress listenAddress, int targetPort) throws IOException {
        return new InterruptingTcpProxy(listenAddress, targetPort);
    }

    /**
     * @return 代理监听的端口
     */
    public int port() {
        return server.getLocalPort();
    }

    /**
     * @return 至今接受过的调用端连接数（重连会让它增加）
     */
    public int acceptedConnections() {
        return accepted.get();
    }

    /**
     * 把现有连接的两端都立即复位。
     */
    public void resetAll() {
        for (Pair pair : pairs) {
            pair.reset();
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
        resetAll();
        threads.shutdownNow();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                Socket upstream = new Socket();
                upstream.connect(target, 1000);
                client.setTcpNoDelay(true);
                upstream.setTcpNoDelay(true);
                Pair pair = new Pair(client, upstream);
                pairs.add(pair);
                accepted.incrementAndGet();
                threads.execute(() -> pump(pair, client, upstream));
                threads.execute(() -> pump(pair, upstream, client));
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void pump(Pair pair, Socket from, Socket to) {
        byte[] buffer = new byte[16 * 1024];
        try {
            InputStream input = from.getInputStream();
            OutputStream output = to.getOutputStream();
            for (int read; (read = input.read(buffer)) != -1; ) {
                output.write(buffer, 0, read);
                output.flush();
            }
        } catch (IOException ignored) {
            // 任一端复位或关闭即结束这个方向的转发。
        }
        pair.reset();
    }

    private final class Pair {

        private final Socket client;

        private final Socket upstream;

        private Pair(Socket client, Socket upstream) {
            this.client = client;
            this.upstream = upstream;
        }

        private void reset() {
            pairs.remove(this);
            reset(client);
            reset(upstream);
        }

        private static void reset(Socket socket) {
            try {
                socket.setSoLinger(true, 0);
                socket.close();
            } catch (IOException ignored) {
                // 已关闭。
            }
        }
    }
}
