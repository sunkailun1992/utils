package com.kellen.log.service;

import com.kellen.log.entity.ElasticSearchRequestLog;

/**
 * Created with IntelliJ IDEA.
 *
 * @author sunkailun
 * @DateTime 2018/7/16  上午11:09
 * @email 376253703@qq.com
 * 
 * @explain
 */
public interface ElasticSearchRequestLogService {
    /**
     * 操作日志，插入
     *
     * @param elasticSearchRequestLog:
     * @return void
     * @author sunkailun
     * @DateTime 2018/7/16  上午11:12
     * @email 376253703@qq.com
     * 
     */
    void insert(ElasticSearchRequestLog elasticSearchRequestLog);

}
