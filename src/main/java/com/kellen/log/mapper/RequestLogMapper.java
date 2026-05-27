package com.kellen.log.mapper;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.log.entity.RequestLog;
import com.kellen.log.entity.RequestLogQuery;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;

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
@Repository
public class RequestLogMapper {
    /**
     * 由springboot自动注入，默认配置会产生mongoTemplate这个bean
     */
    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 操作日志,插入数据
     *
     * @param requestLog:
     * @return void
     * @author 孙凯伦
     * @DateTime 2018/5/23  上午10:22
     * @email 376253703@qq.com
     * 
     */
    public void insert(RequestLog requestLog) {
        mongoTemplate.insert(requestLog);
    }

    /**
     * 操作日志,id查询
     *
     * @param id:
     * @return com.entity.mongodb.user.OperationLog
     * @author 孙凯伦
     * @DateTime 2018/5/23  上午10:23
     * @email 376253703@qq.com
     * 
     */
    public RequestLog select(String id) {
        return mongoTemplate.findOne(new Query(Criteria.where("_id").is(id)), RequestLog.class);
    }

    /**
     * 操作日志,查询所有记录
     *
     * @param :
     * @return java.util.List<com.entity.mongodb.user.OperationLog>
     * @author 孙凯伦
     * @DateTime 2018/5/23  上午10:25
     * @email 376253703@qq.com
     * 
     */
    public List<RequestLog> selectAll() {
        return mongoTemplate.findAll(RequestLog.class);
    }

    /**
     * 操作日志,日期区间删除
     *
     * @return void
     * @author 孙凯伦
     * @DateTime 2018/5/23  上午10:25
     * @email 376253703@qq.com
     * 
     */
    public void delete(Date createDateStart, Date createDateEnd) {
        Query query = new Query(Criteria.where("createDate").gte(DateUtil.offsetHour(createDateStart, 8)).lte(DateUtil.offsetHour(createDateEnd, 8)));
        mongoTemplate.remove(query, RequestLog.class);
    }


    /**
     * 操作日志，查询总数
     *
     * @param requestLog:
     * @param pageable:
     * @return java.util.List<com.entity.mongodb.user.OperationLog>
     * @author 孙凯伦
     * @DateTime 2018/5/23  上午10:25
     * @email 376253703@qq.com
     * 
     */
    public Long selectTotal(List<String> urlList, Date createDateStart, Date createDateEnd, RequestLog requestLog, Pageable pageable) {
        Criteria criteria = new Criteria();
        //操作人名称查询
        if (StringUtils.isNotBlank(requestLog.getName())) {
            criteria.and("name").is(requestLog.getName());
        }
        //操作人序列查询
        if (Objects.nonNull(requestLog.getUserId())) {
            criteria.and("userId").is(requestLog.getUserId());
        }
        //账号查询
        if (StringUtils.isNotBlank(requestLog.getUsername())) {
            criteria.and("username").is(requestLog.getUsername());
        }
        //接口名称查询
        if (StringUtils.isNotBlank(requestLog.getInterfaceName())) {
            criteria.and("interfaceName").is(requestLog.getInterfaceName());
        }
        //请求地址查询
        if (urlList != null) {
            criteria.and("url").in(urlList);
        }
        //时间区间查询
        if (createDateStart != null && createDateEnd != null) {
            criteria.and("createDateTime").gte(DateUtil.offsetHour(createDateStart, 8)).lte(DateUtil.offsetHour(createDateEnd, 8));
        }
        //模糊查询
        Query query = new Query(criteria);
        return mongoTemplate.count(query, RequestLog.class);
    }

    /**
     * 操作日志，批量新增
     *
     * @param requestLog:
     * @return void
     * @author 孙凯伦
     * @DateTime 2018/5/23  上午10:25
     * @email 376253703@qq.com
     * 
     */
    public void insertAll(List<RequestLog> requestLog) {
        mongoTemplate.insertAll(requestLog);
    }


    /**
     * @description: 操作日志 分页查询
     * 　* @author 孙凯伦
     * 　* @date 2021/5/17
     */
    public Page pageEnhance(RequestLogQuery requestLogQuery, int current, int size) {
        Criteria criteria = new Criteria();
        //操作人名称查询
        if (StringUtils.isNotBlank(requestLogQuery.getName())) {
            criteria.and("name").is(requestLogQuery.getName());
        }
        //操作人序列查询
        if (Objects.nonNull(requestLogQuery.getUserId())) {
            criteria.and("userId").is(requestLogQuery.getUserId());
        }
        //模块名称查询
        if (StringUtils.isNotBlank(requestLogQuery.getSystemName())) {
            criteria.and("systemName").is(requestLogQuery.getSystemName());
        }
        //时间区间查询
        if (requestLogQuery.getCreateDateStart() != null && requestLogQuery.getCreateDateEnd() != null) {
            criteria.and("createDateTime")
                    .gte(LocalDateTimeUtil.offset(requestLogQuery.getCreateDateStart(), 8, ChronoUnit.HOURS))
                    .lte(LocalDateTimeUtil.offset(requestLogQuery.getCreateDateEnd(), 8, ChronoUnit.HOURS));
        }
        //定义条件
        Query query = new Query(criteria).skip((current - 1) * size).limit(size);

        //desc
        if(!StringUtils.isEmpty(requestLogQuery.getFieldDesc())){
            query.with(Sort.by(Sort.Order.desc(requestLogQuery.getFieldDesc())));
        } else {
            query.with(Sort.by(Sort.Order.desc("modifyDateTime")));
        }

        //分页查询,先查询日志记录
        List<RequestLog> logList = mongoTemplate.find(query, RequestLog.class);
        //再查询总条数
        long total = mongoTemplate.count(new Query(criteria), RequestLog.class);
        //塞到Page对象中去
        Page page = new Page();
        //设置记录
        page.setRecords(logList);
        //设置当前页
        page.setCurrent(current);
        //设置每页数量
        page.setSize(size);
        //设置总数
        page.setTotal(total);
        return page;
    }
}
