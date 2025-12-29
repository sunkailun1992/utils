package com.gb.log.mapper;

import com.gb.log.entity.ElasticSearchRequestLog;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Created with IntelliJ IDEA.
 * 操作日志
 *
 * @author sunkailun
 * @DateTime 2018/4/23  下午3:07
 * @email 376253703@qq.com
 * 
 * @explain
 */
public interface ElasticSearchRequestLogMapper extends ElasticsearchRepository<ElasticSearchRequestLog, String> {

}
