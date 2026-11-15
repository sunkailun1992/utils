package com.kellen.utils.exception;

import com.kellen.utils.enumeration.ReturnCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证统一异常处理：Spring MVC 标准异常保留标准状态码并换成统一报文，未知异常回 500，
 * 已有业务处理仍回 200，Spring Security 的鉴权异常原样抛出，响应已开始输出时不再写报文。
 */
class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ProbeAdvice())
                .build();
    }

    @Test
    void unreadableJsonReturns400WithUnifiedBody() throws Exception {
        mockMvc.perform(post("/probe/json").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0400"))
                .andExpect(jsonPath("$.errorMessage").value("请求体 JSON 格式不正确"))
                .andExpect(jsonPath("$.msg").doesNotExist());
    }

    @Test
    void missingParameterReturns400WithUnifiedBody() throws Exception {
        mockMvc.perform(get("/probe/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"))
                .andExpect(jsonPath("$.errorMessage").value("请求参数不正确"));
    }

    @Test
    void unknownPathReturns404WithUnifiedBody() throws Exception {
        mockMvc.perform(get("/probe/none"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.errorMessage").value("请求的接口不存在"));
    }

    @Test
    void unsupportedMethodReturns405WithUnifiedBody() throws Exception {
        mockMvc.perform(delete("/probe/param"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.errorMessage").value("请求无法处理"));
    }

    @Test
    void unexpectedExceptionReturns500WithUnifiedBody() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("B0001"))
                .andExpect(jsonPath("$.errorMessage").value("系统处理失败，请查看服务日志"));
    }

    @Test
    void committedResponseIsLeftAlone() throws Exception {
        mockMvc.perform(get("/probe/partial"))
                .andExpect(content().string("partial"));
    }

    @Test
    void bodyValidationStillReturns200() throws Exception {
        mockMvc.perform(post("/probe/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0410"));
    }

    @Test
    void businessExceptionStillReturns200() throws Exception {
        mockMvc.perform(get("/probe/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0210"))
                .andExpect(jsonPath("$.errorMessage").value("x"));
    }

    @Test
    void sqlExceptionMessageIsNotExposed() throws Exception {
        mockMvc.perform(get("/probe/sql"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("C0300"))
                .andExpect(jsonPath("$.errorMessage").value("数据库服务出错"));
    }

    @Test
    void rpcExceptionMessageIsNotExposed() throws Exception {
        mockMvc.perform(get("/probe/rpc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("C0110"))
                .andExpect(jsonPath("$.errorMessage").value("RPC服务出错"));
    }

    @Test
    void securityExceptionsAreRethrown() {
        assertThatThrownBy(() -> mockMvc.perform(get("/probe/denied")))
                .hasCauseInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> mockMvc.perform(get("/probe/unauthenticated")))
                .hasCauseInstanceOf(BadCredentialsException.class);
    }

    @RestControllerAdvice
    static class ProbeAdvice extends ApiExceptionHandler {
    }

    @RestController
    static class ProbeController {

        @PostMapping("/probe/json")
        String json(@RequestBody NamedBody body) {
            return body.name();
        }

        @GetMapping("/probe/param")
        String param(@RequestParam("q") String q) {
            return q;
        }

        @PostMapping("/probe/valid")
        String valid(@Valid @RequestBody NamedBody body) {
            return body.name();
        }

        @GetMapping("/probe/user")
        String user() {
            throw new UserException(ReturnCode.USER_PASSWORD_ERROR, "x");
        }

        @GetMapping("/probe/sql")
        String sql() throws SQLException {
            throw new SQLException("ERROR: duplicate key value violates unique constraint \"auth_user_pkey\" Detail: Key (id)=(u-1)", "23505");
        }

        @GetMapping("/probe/rpc")
        String rpc() {
            throw new RpcException("downstream user-service at 10.0.0.8:20880 timed out");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("unexpected");
        }

        @GetMapping("/probe/denied")
        String denied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/probe/unauthenticated")
        String unauthenticated() {
            throw new BadCredentialsException("bad credentials");
        }

        @GetMapping("/probe/partial")
        void partial(HttpServletResponse response) throws IOException {
            response.getWriter().write("partial");
            response.flushBuffer();
            throw new IllegalStateException("late failure");
        }
    }

    record NamedBody(@NotBlank String name) {
    }
}
