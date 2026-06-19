package com.kellen.log.service;

import com.kellen.log.entity.ElasticSearchRequestLog;

/**
 *
 * @author 孙凯伦
 * 
 */
public interface ElasticSearchRequestLogService {
    /**
     * 操作日志，插入
     *
     * @param elasticSearchRequestLog:
     * @return void
     * @author 孙凯伦
     * 
     */
    void insert(ElasticSearchRequestLog elasticSearchRequestLog);

}
