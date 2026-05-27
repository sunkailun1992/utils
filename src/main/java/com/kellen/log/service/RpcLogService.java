package com.kellen.log.service;

import com.kellen.log.entity.RpcLog;

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
public interface RpcLogService {

    /**
     * 日志记录
     * @author 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: rpcLog
     * @param receiveServer
     * @param api
     * @param parameter
     * @param error
     * @return: void
     * @date: 2022/1/18 10:23 AM
     *
     */
    void rpcLog(String receiveServer,String api, Object parameter, String error);

    /**
     * 操作日志，插入
     * @author 孙凯伦
     * @DateTime    2018/7/16  上午11:12
     * @email       376253703@qq.com
     * 
     * @param rpcLog:
     * @return      void
     */
    void insert(RpcLog rpcLog);

    /**
     * 操作日志，单条查询
     * @author 孙凯伦
     * @DateTime    2018/7/16  上午11:12
     * @email       376253703@qq.com
     * 
     * @param id: 
     * @return      com.entity.mongodb.log.Log
     */
    RpcLog select(String id);

    /**
     * 时间删除
     * @author 孙凯伦
     * @DateTime    2018/7/16  上午11:12
     * @email       376253703@qq.com
     * 
     * @param createDateStart:
     * @param createDateEnd:
     * @return      void
     */
    void delete(Date createDateStart, Date createDateEnd);

}
