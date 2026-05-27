package com.kellen.log.mapper;

import com.kellen.log.entity.ElasticSearchRequestLog;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Created with IntelliJ IDEA.
 * 操作日志
 *
 * @author 孙凯伦
 * @DateTime 2018/4/23  下午3:07
 * @email 376253703@qq.com
 * 
 * @explain
 */
public interface ElasticSearchRequestLogMapper extends ElasticsearchRepository<ElasticSearchRequestLog, String> {

}
