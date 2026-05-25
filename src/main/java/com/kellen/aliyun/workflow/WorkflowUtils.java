package com.kellen.aliyun.workflow;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.fnf.model.v20190315.*;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.kellen.aliyun.AliyunKey;

/**
 * TODO 工作流工具类
 *
 * @author 孙凯伦
 * @className Workflow
 * @email 376253703@qq.com
 * 
 * @time 2022/2/23 10:05 AM
 */
public class WorkflowUtils {


    /**
     * TODO 初始化客户端
     *
     * @param workflowRegion
     * @return com.aliyuncs.IAcsClient
     * @throws
     * @author 孙凯伦
     * @email 376253703@qq.com
     * 
     * @methodName initialize
     * @time 2022/2/23 10:26 AM
     */
    public static IAcsClient initialize(WorkflowRegion workflowRegion) {
        DefaultProfile profile = DefaultProfile.getProfile(workflowRegion.getType(), AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        IAcsClient client = new DefaultAcsClient(profile);
        return client;
    }


    /**
     * TODO 发起一次执行
     *
     * @param fnfClient
     * @param flowName
     * @param id
     * @param data
     * @return com.aliyuncs.fnf.model.v20190315.StartExecutionResponse
     * @throws
     * @author 孙凯伦
     * @email 376253703@qq.com
     * 
     * @methodName execution
     * @time 2022/2/23 10:15 AM
     */
    public static StartExecutionResponse execution(IAcsClient fnfClient, String flowName, String id, String data) throws ClientException {
        StartExecutionRequest request = new StartExecutionRequest();
        request.setFlowName(flowName);
        request.setExecutionName(id);
        request.setInput(data);
        return fnfClient.getAcsResponse(request);
    }


    /**
     * TODO 查询执行结果
     *
     * @param fnfClient
     * @param flowName
     * @param id
     * @return com.aliyuncs.fnf.model.v20190315.DescribeExecutionResponse
     * @throws
     * @author 孙凯伦
     * @email 376253703@qq.com
     * 
     * @methodName results
     * @time 2022/2/23 10:28 AM
     */
    public static DescribeExecutionResponse results(IAcsClient fnfClient, String flowName, String id) throws ClientException {
        DescribeExecutionRequest request = new DescribeExecutionRequest();
        request.setFlowName(flowName);
        request.setExecutionName(id);
        return fnfClient.getAcsResponse(request);
    }


    /**
     * TODO 异步回调通知结果
     *
     * @param fnfClient
     * @param token
     * @param data
     * @return com.aliyuncs.http.HttpResponse
     * @throws
     * @author 孙凯伦
     * @email 376253703@qq.com
     * 
     * @methodName callback
     * @time 2022/2/23 10:28 AM
     */
    public static HttpResponse callback(IAcsClient fnfClient, String token, String data) throws ClientException {
        ReportTaskSucceededRequest request = new ReportTaskSucceededRequest();
        request.setTaskToken(token);
        request.setOutput(data);
        return fnfClient.doAction(request);
    }
}
