package com.kellen.log.service.impl;

import com.kellen.log.entity.RpcLog;
import com.kellen.log.mapper.RpcLogMapper;
import com.kellen.log.service.RpcLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * RPC 调用日志服务实现。
 *
 * @author 孙凯伦
 */
@Service
public class RpcLogServiceImpl implements RpcLogService {

    /**
     * RPC 日志 Mapper。
     */
    @Autowired
    private RpcLogMapper rpcLogMapper;

    /**
     * Spring 应用上下文。
     */
    @Autowired
    private ConfigurableApplicationContext applicationContext;

    /**
     * 记录 RPC 熔断或调用异常日志。
     *
     * @param receiveServer 接收服务名称
     * @param api           RPC 接口地址
     * @param parameter     请求参数
     * @param error         错误信息
     */
    @Async
    @Override
    public void rpcLog(String receiveServer,String api, Object parameter, String error) {
        RpcLog rpcLog = new RpcLog(); // 创建 RPC 日志实体。
        rpcLog.setSendServer(applicationContext.getEnvironment().getProperty("spring.application.name")); // 记录当前发送服务名称。
        rpcLog.setReceiveServer(receiveServer); // 记录接收服务名称。
        rpcLog.setApi(api); // 记录 RPC 接口地址。
        rpcLog.setParameter(parameter); // 记录请求参数。
        rpcLog.setError(error); // 记录错误信息。
        rpcLog.setCreateDateTime(new Date()); // 记录日志创建时间。
        insert(rpcLog); // 异步写入 RPC 日志。
    }

    /**
     * 操作日志，新增
     *
     * @param rpcLog:
     * @return void
     * @author 孙凯伦
     * @DateTime 2018/7/16  上午11:15
     * @email 376253703@qq.com
     * 
     */
    @Async
    @Override
    public void insert(RpcLog rpcLog) {
        rpcLogMapper.insert(rpcLog);
    }


    /**
     * 操作日志，单条查询
     *
     * @param id:
     * @return com.entity.mongodb.log.Log
     * @author 孙凯伦
     * @DateTime 2018/7/16  上午11:15
     * @email 376253703@qq.com
     * 
     */
    @Override
    public RpcLog select(String id) {
        return rpcLogMapper.select(id);
    }

    /**
     * 操作日志，删除
     *
     * @param createDateStart:
     * @param createDateEnd:
     * @return void
     * @author 孙凯伦
     * @DateTime 2018/7/16  上午11:16
     * @email 376253703@qq.com
     * 
     */
    @Override
    public void delete(Date createDateStart, Date createDateEnd) {
        rpcLogMapper.delete(createDateStart, createDateEnd);
    }

}
