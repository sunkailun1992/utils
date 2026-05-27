package com.kellen.log.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.log.entity.RequestLog;
import com.kellen.log.entity.RequestLogQuery;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 *
 * @author 孙凯伦
 * @DateTime 2018/7/16  上午11:09
 * @email 376253703@qq.com
 * 
 * @explain
 */
public interface RequestLogService {
    /**
     * 操作日志，插入
     *
     * @param requestLog:
     * @return void
     * @author 孙凯伦
     * @DateTime 2018/7/16  上午11:12
     * @email 376253703@qq.com
     * 
     */
    void insert(RequestLog requestLog);

    /**
     * 操作日志，单条查询
     *
     * @param id:
     * @return com.entity.mongodb.log.Log
     * @author 孙凯伦
     * @DateTime 2018/7/16  上午11:12
     * @email 376253703@qq.com
     * 
     */
    RequestLog select(String id);

    /**
     * 时间删除
     *
     * @param createDateStart:
     * @param createDateEnd:
     * @return void
     * @author 孙凯伦
     * @DateTime 2018/7/16  上午11:12
     * @email 376253703@qq.com
     * 
     */
    void delete(Date createDateStart, Date createDateEnd);

    /**
     * 操作日志，查询总数
     *
     * @param requestLogQuery:
     * @return java.util.List<com.entity.mongodb.log.Log>
     * @author 孙凯伦
     * @DateTime 2018/7/16  上午11:16
     * @email 376253703@qq.com
     * 
     */
    Long selectTotal(RequestLogQuery requestLogQuery);

    /**
     * 操作日志, 查询分页
     * @author 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: pageEnhance
     * @param requestLogQuery
     * @param pageNumber
     * @param pageSize
     * @return: com.baomidou.mybatisplus.extension.plugins.pagination.Page
     * @date: 2022/1/18 10:29 AM
     *
     */
    Page pageEnhance(RequestLogQuery requestLogQuery, Integer pageNumber, Integer pageSize);
}
