package com.gb.log.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gb.log.entity.RequestLog;
import com.gb.log.entity.RequestLogQuery;
import com.gb.log.service.RequestLogService;
import com.gb.utils.Json;
import com.gb.utils.annotations.Methods;
import com.gb.utils.annotations.RequestRequired;
import com.gb.utils.enumeration.ReturnCode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wangyifei
 * @Description
 * @date 2021/5/17 16:34
 */
@Slf4j
@RequestRequired
@RestController
@RequestMapping("/request-log")
@Api(tags = "全局请求日志")
public class RequestLogController {
    @Autowired
    private RequestLogService requestLogService;

    /**
     * 全局请求日志查询
     *
     * @param requestLogQuery
     * @return com.utils.Json
     * @author 王一飞
     * @since 2021/3/12  17:27
     */
    @Methods(methodsName = "全局请求日志查询", methods = "select")
    @ApiOperation(value = "全局请求日志查询", httpMethod = "GET", notes = "全局请求日志查询", response = Json.class)
    @GetMapping("/select")
    public Json<Page<RequestLog>> select(RequestLogQuery requestLogQuery, Integer pageNumber, Integer pageSize) {
        return new Json(ReturnCode.成功, requestLogService.pageEnhance(requestLogQuery, pageNumber, pageSize));
    }
}
