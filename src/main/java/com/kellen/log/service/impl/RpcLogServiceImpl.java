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
 * Created with IntelliJ IDEA.
 * 操作日志
 * @author sunkailun
 * @DateTime 2018/7/16  上午11:09
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Service
public class RpcLogServiceImpl implements RpcLogService {

    @Autowired
    private RpcLogMapper rpcLogMapper;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    /**
     *
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: rpcLog
     * @description: TODO  rpc熔断记录
     * @param api
     * @return: void
     * @date: 2021/6/11 5:52 下午
     *
     */
    @Async
    @Override
    public void rpcLog(String receiveServer,String api, Object parameter, String error) {
        RpcLog rpcLog = new RpcLog();
        rpcLog.setSendServer(applicationContext.getEnvironment().getProperty("spring.application.name"));
        rpcLog.setReceiveServer(receiveServer);
        rpcLog.setApi(api);
        rpcLog.setParameter(parameter);
        rpcLog.setError(error);
        rpcLog.setCreateDateTime(new Date());
        insert(rpcLog);
    }

    /**
     * 操作日志，新增
     *
     * @param rpcLog:
     * @return void
     * @author sunkailun
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
     * @author sunkailun
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
     * @author sunkailun
     * @DateTime 2018/7/16  上午11:16
     * @email 376253703@qq.com
     * 
     */
    @Override
    public void delete(Date createDateStart, Date createDateEnd) {
        rpcLogMapper.delete(createDateStart, createDateEnd);
    }

}
