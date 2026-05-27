package com.kellen.log.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.log.entity.RequestLog;
import com.kellen.log.entity.RequestLogQuery;
import com.kellen.log.service.RequestLogService;
import com.kellen.utils.ApiResponse;
import com.kellen.utils.annotations.Methods;
import com.kellen.utils.annotations.RequestRequired;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 孙凯伦
 * @Description
 * @date 2021/5/17 16:34
 */
@Slf4j
@RequestRequired
@RestController
@RequestMapping("/request-log")
@Tag(name = "全局请求日志")
public class RequestLogController {
    @Autowired
    private RequestLogService requestLogService;

    /**
     * 全局请求日志查询
     *
     * @param requestLogQuery
     * @return com.kellen.utils.ApiResponse
     * @author 孙凯伦
     * @since 2021/3/12  17:27
     */
    @Methods(methodsName = "全局请求日志查询", methods = "select")
    @Operation(summary = "全局请求日志查询", description = "全局请求日志查询")
    @GetMapping("/select")
    public ApiResponse<Page<RequestLog>> select(RequestLogQuery requestLogQuery, Integer pageNumber, Integer pageSize) {
        return ApiResponse.success(requestLogService.pageEnhance(requestLogQuery, pageNumber, pageSize)); // 使用统一成功工厂方法组装标准响应结构。
    }
}
