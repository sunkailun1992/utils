package com.kellen.log.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.log.entity.RequestLog;
import com.kellen.log.entity.RequestLogQuery;
import com.kellen.log.mapper.RequestLogMapper;
import com.kellen.log.service.RequestLogService;
import com.kellen.utils.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;

/**
 * Created with IntelliJ IDEA.
 * 操作日志
 *
 * @author sunkailun
 * @DateTime 2018/7/16  上午11:09
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Service
public class RequestLogServiceImpl implements RequestLogService {

    @Autowired
    private RequestLogMapper requestLogMapper;

    /**
     * 操作日志，新增
     *
     * @param requestLog:
     * @return void
     * @author sunkailun
     * @DateTime 2018/7/16  上午11:15
     * @email 376253703@qq.com
     * 
     */
    @Async
    @Override
    public void insert(RequestLog requestLog) {
        requestLogMapper.insert(requestLog);
    }


    /**
     * 操作日志，单条查询
     *
     * @param id:
     * @return com.entity.mongodb.log.Log
     * @author sunkailun
     * @DateTime 2018/7/16  上午11:15
     * @email 376253703@qq.com
     * 
     */
    @Override
    public RequestLog select(String id) {
        return requestLogMapper.select(id);
    }

    /**
     * 操作日志，删除
     *
     * @param createDateStart:
     * @param createDateEnd:
     * @return void
     * @author sunkailun
     * @DateTime 2018/7/16  上午11:16
     * @email 376253703@qq.com
     * 
     */
    @Override
    public void delete(Date createDateStart, Date createDateEnd) {
        requestLogMapper.delete(createDateStart, createDateEnd);
    }


    /**
     * 操作日志，查询总数
     *
     * @param requestLogQuery:
     * @return java.util.List<com.entity.mongodb.log.Log>
     * @author sunkailun
     * @DateTime 2018/7/16  上午11:16
     * @email 376253703@qq.com
     * 
     */
    @Override
    public Long selectTotal(RequestLogQuery requestLogQuery) {
        return requestLogMapper.selectTotal(requestLogQuery.getUrlList(),
                Date.from(requestLogQuery.getCreateDateStart().atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(requestLogQuery.getCreateDateEnd().atZone(ZoneId.systemDefault()).toInstant()),
                requestLogQuery.getRequestLog(), requestLogQuery.getPageable());
    }


    /**
     * @description: 操作日志 分页查询
     * 　* @author wangyifei
     * 　* @date 2021/5/17
     */
    @Override
    public Page pageEnhance(RequestLogQuery requestLogQuery, Integer pageNumber, Integer pageSize) {
        if (Objects.isNull(pageNumber) || Objects.isNull(pageSize)) {
            throw new BusinessException("pageSize 或 pageSize 为null!");
        }
        return requestLogMapper.pageEnhance(requestLogQuery, pageNumber, pageSize);
    }
}
