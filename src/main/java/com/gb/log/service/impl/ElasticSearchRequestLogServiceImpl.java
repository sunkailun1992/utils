package com.gb.log.service.impl;

import com.gb.log.entity.ElasticSearchRequestLog;
import com.gb.log.mapper.ElasticSearchRequestLogMapper;
import com.gb.log.service.ElasticSearchRequestLogService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
public class ElasticSearchRequestLogServiceImpl implements ElasticSearchRequestLogService {
    @Resource
    private ElasticSearchRequestLogMapper elasticSearchRequestLogMapper;


    /**
     * 操作日志，新增
     *
     * @param elasticSearchRequestLog:
     * @return void
     * @author sunkailun
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
