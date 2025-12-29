package com.gb.log.mapper;

import cn.hutool.core.date.DateUtil;
import com.gb.log.entity.RpcLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * 操作日志
 * @author sunkailun
 * @DateTime 2018/4/23  下午3:07
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Repository
public class RpcLogMapper {
    /**
     * 由springboot自动注入，默认配置会产生mongoTemplate这个bean
     */
    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 操作日志,插入数据
     *
     * @param rpcLog:
     * @return void
     * @author sunkailun
     * @DateTime 2018/5/23  上午10:22
     * @email 376253703@qq.com
     * 
     */
    public void insert(RpcLog rpcLog) {
        mongoTemplate.insert(rpcLog);
    }

    /**
     * 操作日志,id查询
     *
     * @param id:
     * @return com.entity.mongodb.user.OperationLog
     * @author sunkailun
     * @DateTime 2018/5/23  上午10:23
     * @email 376253703@qq.com
     * 
     */
    public RpcLog select(String id) {
        return mongoTemplate.findOne(new Query(Criteria.where("_id").is(id)), RpcLog.class);
    }

    /**
     * 操作日志,查询所有记录
     *
     * @param :
     * @return java.util.List<com.entity.mongodb.user.OperationLog>
     * @author sunkailun
     * @DateTime 2018/5/23  上午10:25
     * @email 376253703@qq.com
     * 
     */
    public List<RpcLog> selectAll() {
        return mongoTemplate.findAll(RpcLog.class);
    }

    /**
     * 操作日志,日期区间删除
     *
     * @return void
     * @author sunkailun
     * @DateTime 2018/5/23  上午10:25
     * @email 376253703@qq.com
     * 
     */
    public void delete(Date createDateStart, Date createDateEnd) {
        Query query = new Query(Criteria.where("createDate").gte(DateUtil.offsetHour(createDateStart, 8)).lte(DateUtil.offsetHour(createDateEnd, 8)));
        mongoTemplate.remove(query, RpcLog.class);
    }

    /**
     * 操作日志，集合查询
     *
     * @param rpcLog:
     * @param pageable:
     * @return java.util.List<com.entity.mongodb.user.OperationLog>
     * @author sunkailun
     * @DateTime 2018/5/23  上午10:25
     * @email 376253703@qq.com
     * 
     */
    public List<RpcLog> selectList(List<String> urlList, Date createDateStart, Date createDateEnd, RpcLog rpcLog, Pageable pageable) {
        Criteria criteria = new Criteria();
        //发送服务查询
        if (StringUtils.isNotBlank(rpcLog.getSendServer())) {
            criteria.and("sendServer").is(rpcLog.getSendServer());
        }
        //接收服务查询
        if (StringUtils.isNotBlank(rpcLog.getReceiveServer())) {
            criteria.and("receiveServer").is(rpcLog.getReceiveServer());
        }
        //调用接口查询
        if (StringUtils.isNotBlank(rpcLog.getApi())) {
            criteria.and("api").is(rpcLog.getApi());
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
        Query query = new Query(criteria).with(pageable);
        List<RpcLog> list = mongoTemplate.find(query, RpcLog.class);
        return list;
    }

    /**
     * 操作日志，查询总数
     *
     * @param rpcLog:
     * @param pageable:
     * @return java.util.List<com.entity.mongodb.user.OperationLog>
     * @author sunkailun
     * @DateTime 2018/5/23  上午10:25
     * @email 376253703@qq.com
     * 
     */
    public Long selectTotal(List<String> urlList, Date createDateStart, Date createDateEnd, RpcLog rpcLog, Pageable pageable) {
        Criteria criteria = new Criteria();
        //发送服务查询
        if (StringUtils.isNotBlank(rpcLog.getSendServer())) {
            criteria.and("sendServer").is(rpcLog.getSendServer());
        }
        //接收服务查询
        if (StringUtils.isNotBlank(rpcLog.getReceiveServer())) {
            criteria.and("receiveServer").is(rpcLog.getReceiveServer());
        }
        //调用接口查询
        if (StringUtils.isNotBlank(rpcLog.getApi())) {
            criteria.and("api").is(rpcLog.getApi());
        }
        //时间区间查询
        if (createDateStart != null && createDateEnd != null) {
            criteria.and("createDateTime").gte(DateUtil.offsetHour(createDateStart, 8)).lte(DateUtil.offsetHour(createDateEnd, 8));
        }
        //模糊查询
        Query query = new Query(criteria);
        Long total = mongoTemplate.count(query, RpcLog.class);
        return total;
    }

    /**
     * 操作日志，批量新增
     *
     * @param rpcLog:
     * @return void
     * @author sunkailun
     * @DateTime 2018/5/23  上午10:25
     * @email 376253703@qq.com
     * 
     */
    public void insertAll(List<RpcLog> rpcLog) {
        mongoTemplate.insertAll(rpcLog);
    }
}
