package com.kellen.config.seata;

/**
 * 仅在测试 classpath 上存在的跨进程事务探针，不属于业务 RPC 或发布制品。
 */
public interface SeataProbeRpcService {

    /** 下游先提交真实 AT 分支，再按要求抛错以触发两个已提交分支的补偿。 */
    String[] write(String caseId, boolean failAfterCommit);
}
