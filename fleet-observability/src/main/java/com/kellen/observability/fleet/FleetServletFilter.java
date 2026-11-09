package com.kellen.observability.fleet;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.concurrent.TimeoutException;

/** Servlet 实读/实写 tee；保留 flush 与 async 语义，不缓存整个响应或预读请求。 */
public final class FleetServletFilter extends OncePerRequestFilter {
    private static final String STATE = FleetServletFilter.class.getName() + ".state";
    private final FleetObservabilityProperties properties;
    private final SafeBodyProjector projector;
    private final FleetEventSink sink;

    public FleetServletFilter(FleetObservabilityProperties properties, SafeBodyProjector projector, FleetEventSink sink) {
        this.properties = properties; this.projector = projector; this.sink = sink;
    }
    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ServletExchange exchange = (ServletExchange) request.getAttribute(STATE);
        if (exchange == null) {
            exchange = new ServletExchange(request, response);
            request.setAttribute(STATE, exchange);
        } else if (request.getDispatcherType() == DispatcherType.ERROR || request.getDispatcherType() == DispatcherType.ASYNC) {
            // Redispatch 使用容器的当前路径/dispatcher视图；嵌套tee由重入计数排除重复。
            if (request.getDispatcherType() == DispatcherType.ERROR && !response.isCommitted()) exchange.log.response.reset();
            exchange.response = new TeeResponse(response, exchange);
            exchange.request = new TeeRequest(request, exchange);
        }
        if (request.getDispatcherType() == DispatcherType.ERROR) exchange.errorDispatchExpected = false;
        try { chain.doFilter(exchange.request, exchange.response); }
        catch (IOException | ServletException | RuntimeException failure) {
            exchange.failed(failure);
            if (response.isCommitted()) exchange.transportFailed = true;
            if (!exchange.transportFailed && request.getDispatcherType() != DispatcherType.ERROR) exchange.errorDispatchExpected = true;
            throw failure;
        }
        finally {
            exchange.refresh();
            if (exchange.request.isAsyncStarted()) exchange.listen(exchange.request.getAsyncContext());
            else if (!exchange.errorDispatchExpected) exchange.complete();
        }
    }
    @Override protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException { doFilterInternal(request, response, chain); }

    private final class ServletExchange implements AsyncListener {
        final HttpExchangeLog log;
        TeeRequest request;
        TeeResponse response;
        private AsyncContext registered;
        volatile boolean errorDispatchExpected;
        volatile boolean transportFailed;
        final ThreadLocal<Integer> readDepth = ThreadLocal.withInitial(() -> 0);
        final ThreadLocal<Integer> writeDepth = ThreadLocal.withInitial(() -> 0);
        ServletExchange(HttpServletRequest originalRequest, HttpServletResponse originalResponse) {
            log = new HttpExchangeLog(originalRequest.getMethod(), contentType(originalRequest.getContentType(), originalRequest.getHeader("Content-Encoding")),
                    projector, sink, properties.getMaxBodyBytes());
            response = new TeeResponse(originalResponse, this);
            request = new TeeRequest(originalRequest, this);
            if (originalRequest.getContentLengthLong() == 0) log.request.completed();
        }
        void refresh() {
            if (request.getDispatcherType() != DispatcherType.ERROR && request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof org.springframework.web.method.HandlerMethod)
                log.matchedRoute(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
            log.responseType = contentType(response.getContentType(), response.getHeader("Content-Encoding"));
        }
        synchronized void listen(AsyncContext context) {
            if (registered == context) return;
            try { context.addListener(this, request, response); registered = context; }
            catch (IllegalStateException alreadyCompleted) { complete(); }
        }
        void complete() {
            if (!transportFailed) response.flushWriter();
            refresh();
            int status = response.getStatus();
            log.finish(log.failure != null && status < 400 && !response.isCommitted() ? null : status,
                    log.outcome, log.failure, !transportFailed);
        }
        void failed(Throwable failure) {
            log.failure = failure;
            if (failure instanceof java.util.concurrent.CancellationException
                    || org.springframework.web.util.DisconnectedClientHelper.isClientDisconnectedException(failure)) {
                log.outcome = "cancelled"; transportFailed = true;
            }
        }
        @Override public void onComplete(AsyncEvent event) { complete(); }
        @Override public void onTimeout(AsyncEvent event) { log.outcome = "timeout"; log.failure = new TimeoutException(); }
        @Override public void onError(AsyncEvent event) { if (event.getThrowable() != null) failed(event.getThrowable()); }
        @Override public synchronized void onStartAsync(AsyncEvent event) { registered = null; listen(event.getAsyncContext()); }
    }

    private static String contentType(String type, String encoding) {
        return encoding == null || encoding.equalsIgnoreCase("identity") ? type : "application/octet-stream";
    }
    private final class TeeRequest extends HttpServletRequestWrapper {
        private final ServletExchange exchange;
        private ServletInputStream input;
        private BufferedReader reader;
        TeeRequest(HttpServletRequest request, ServletExchange exchange) { super(request); this.exchange = exchange; }
        @Override public ServletInputStream getInputStream() throws IOException {
            if (reader != null) throw new IllegalStateException("reader already obtained");
            return input();
        }
        private ServletInputStream input() throws IOException {
            if (input == null) {
                ServletInputStream source = super.getInputStream();
                input = new ServletInputStream() {
                    @Override public int read() throws IOException {
                        int depth = exchange.readDepth.get(); exchange.readDepth.set(depth + 1);
                        int value;
                        try { value = source.read(); } finally { restore(exchange.readDepth, depth); }
                        if (value < 0) exchange.log.request.completed();
                        else if (depth == 0) record(new byte[]{(byte) value}, 0, 1);
                        return value;
                    }
                    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                        int depth = exchange.readDepth.get(); exchange.readDepth.set(depth + 1);
                        int count;
                        try { count = source.read(bytes, offset, length); } finally { restore(exchange.readDepth, depth); }
                        if (count < 0) exchange.log.request.completed(); else if (count > 0 && depth == 0) record(bytes, offset, count);
                        return count;
                    }
                    private void record(byte[] bytes, int offset, int count) {
                        exchange.refresh(); exchange.log.request.add(bytes, offset, count, exchange.log.captureMedia(exchange.log.requestType));
                        if (source.isFinished()) exchange.log.request.completed();
                    }
                    @Override public boolean isFinished() { return source.isFinished(); }
                    @Override public boolean isReady() { return source.isReady(); }
                    @Override public void setReadListener(ReadListener listener) { source.setReadListener(listener); }
                    @Override public void close() throws IOException { source.close(); }
                };
            }
            return input;
        }
        @Override public BufferedReader getReader() throws IOException {
            if (reader == null) {
                if (input != null) throw new IllegalStateException("stream already obtained");
                String encoding = getCharacterEncoding();
                reader = new BufferedReader(new InputStreamReader(input(), encoding == null ? java.nio.charset.StandardCharsets.ISO_8859_1 : Charset.forName(encoding)));
            }
            return reader;
        }
        @Override public AsyncContext startAsync() { AsyncContext context = super.startAsync(this, exchange.response); exchange.listen(context); return context; }
        @Override public AsyncContext startAsync(ServletRequest request, ServletResponse response) {
            AsyncContext context = super.startAsync(request, response); exchange.listen(context); return context;
        }
    }
    private final class TeeResponse extends HttpServletResponseWrapper {
        private final ServletExchange exchange;
        private ServletOutputStream output;
        private PrintWriter writer;
        private CountingWriter countingWriter;
        TeeResponse(HttpServletResponse response, ServletExchange exchange) { super(response); this.exchange = exchange; }
        @Override public ServletOutputStream getOutputStream() throws IOException {
            if (writer != null) throw new IllegalStateException("writer already obtained");
            return output();
        }
        private ServletOutputStream output() throws IOException {
            if (output == null) {
                ServletOutputStream target = super.getOutputStream();
                output = new ServletOutputStream() {
                    @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}, 0, 1); }
                    @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                        int depth = exchange.writeDepth.get(); exchange.writeDepth.set(depth + 1);
                        try { target.write(bytes, offset, length); }
                        catch (IOException failure) { exchange.failed(failure); exchange.transportFailed = true; throw failure; }
                        finally { restore(exchange.writeDepth, depth); }
                        if (depth == 0) { exchange.refresh(); exchange.log.response.add(bytes, offset, length, exchange.log.captureMedia(exchange.log.responseType)); }
                    }
                    @Override public void flush() throws IOException {
                        try { target.flush(); } catch (IOException failure) { exchange.failed(failure); exchange.transportFailed = true; throw failure; }
                    }
                    @Override public void close() throws IOException {
                        try { target.close(); } catch (IOException failure) { exchange.failed(failure); exchange.transportFailed = true; throw failure; }
                    }
                    @Override public boolean isReady() { return target.isReady(); }
                    @Override public void setWriteListener(WriteListener listener) { target.setWriteListener(listener); }
                };
            }
            return output;
        }
        @Override public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                if (output != null) throw new IllegalStateException("stream already obtained");
                PrintWriter original = super.getWriter();
                countingWriter = new CountingWriter(original, Charset.forName(getCharacterEncoding()), exchange);
                writer = new PrintWriter(countingWriter) {
                    @Override public boolean checkError() { return super.checkError() || original.checkError(); }
                };
            }
            return writer;
        }
        @Override public void flushBuffer() throws IOException { if (writer != null) writer.flush(); super.flushBuffer(); }
        @Override public void sendError(int status) throws IOException { exchange.errorDispatchExpected = true; super.sendError(status); }
        @Override public void sendError(int status, String message) throws IOException { exchange.errorDispatchExpected = true; super.sendError(status, message); }
        @Override public void resetBuffer() {
            super.resetBuffer(); exchange.log.response.reset(); if (countingWriter != null) countingWriter.resetEncoding();
        }
        @Override public void reset() { super.reset(); exchange.log.response.reset(); writer = null; output = null; countingWriter = null; }
        void flushWriter() {
            if (writer != null) {
                writer.flush();
                if (writer.checkError()) { exchange.log.failure = new IOException(); exchange.transportFailed = true; }
                if (countingWriter.hasPendingCharacter()) { exchange.log.responseBytesKnown = false; exchange.transportFailed = true; }
            }
        }
    }

    /** 原 writer 保留容器的缓冲/reset/flush；仅用相同charset增量计算已接受字符的字节投影。 */
    private final class CountingWriter extends Writer {
        private final Writer delegate;
        private final CharsetEncoder encoder;
        private final ByteBuffer bytes = ByteBuffer.allocate(512);
        private final ServletExchange exchange;
        private Character pending;
        CountingWriter(Writer delegate, Charset charset, ServletExchange exchange) {
            this.delegate = delegate; this.exchange = exchange;
            encoder = charset.newEncoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        }
        @Override public void write(char[] chars, int offset, int length) throws IOException {
            int depth = exchange.writeDepth.get(); exchange.writeDepth.set(depth + 1);
            try { delegate.write(chars, offset, length); } finally { restore(exchange.writeDepth, depth); }
            if (depth == 0) observe(CharBuffer.wrap(chars, offset, length));
        }
        @Override public void write(String value, int offset, int length) throws IOException {
            int depth = exchange.writeDepth.get(); exchange.writeDepth.set(depth + 1);
            try { delegate.write(value, offset, length); } finally { restore(exchange.writeDepth, depth); }
            if (depth == 0) observe(CharBuffer.wrap(value, offset, offset + length));
        }
        private void observe(CharBuffer input) {
            exchange.refresh();
            if (pending != null && input.hasRemaining()) {
                char previous = pending; pending = null;
                encode(CharBuffer.wrap(new char[]{previous, input.get()}));
            }
            encode(input);
        }
        private void encode(CharBuffer input) {
            while (input.hasRemaining()) {
                var result = encoder.encode(input, bytes, false);
                bytes.flip(); exchange.log.response.add(bytes, exchange.log.captureMedia(exchange.log.responseType)); bytes.clear();
                if (result.isUnderflow()) {
                    if (input.hasRemaining()) pending = input.get();
                    break;
                }
            }
        }
        boolean hasPendingCharacter() { return pending != null; }
        void resetEncoding() { encoder.reset(); bytes.clear(); pending = null; }
        @Override public void flush() throws IOException { delegate.flush(); }
        @Override public void close() throws IOException { delegate.close(); }
    }
    private static void restore(ThreadLocal<Integer> depth, int previous) {
        if (previous == 0) depth.remove(); else depth.set(previous);
    }
}
