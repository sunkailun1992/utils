package com.kellen.log.service.impl;

import com.kellen.log.entity.ElasticSearchRequestLog;
import com.kellen.log.mapper.ElasticSearchRequestLogMapper;
import com.kellen.log.service.ElasticSearchRequestLogService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * Created with IntelliJ IDEA.
 * 操作日志
 *
 * @author 孙凯伦
 * @DateTime 2018/7/16  上午11:09
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Service
public class ElasticSearchRequestLogServiceImpl implements ElasticSearchRequestLogService {
    @Resource
    private ElasticSearchRequestLogMapper elasticSearchRequestLogMapper;


    /**
     * 操作日志，新增
     *
     * @param elasticSearchRequestLog:
     * @return void
     * @author 孙凯伦
     * @DateTime 2018/7/16  上午11:15
     * @email 376253703@qq.com
     * 
     */
    @Async
    @Override
    public void insert(ElasticSearchRequestLog elasticSearchRequestLog) {
        elasticSearchRequestLogMapper.save(elasticSearchRequestLog);
    }

}
