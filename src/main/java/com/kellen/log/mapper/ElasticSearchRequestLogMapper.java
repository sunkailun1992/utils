package com.kellen.log.mapper;

import com.kellen.log.entity.ElasticSearchRequestLog;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 操作日志
 *
 * @author 孙凯伦
 * 
 */
public interface ElasticSearchRequestLogMapper extends ElasticsearchRepository<ElasticSearchRequestLog, String> {

}
