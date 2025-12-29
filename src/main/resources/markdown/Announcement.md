# 公告

测试环境：
-------------
1：alpha测试（测试环境）

2：bate测试（预发环境）

3：公测测试（灰度环境）

4：产品验收（正式环境）

严禁正式环境进行测试数据



规则
-------------

融合规则：personal融合dev（两个保持同步版本）禁止不同版本直接融合。

修bug规则：新建bug修复分支，修复完成后，向上所有迭代的版本分支进行融合，删除分支。

正式环境规则：严禁直接修改正式环境，如要修改bug，请走钉钉流程。修复完bug，后需要上所有迭代的版本分支进行融合。

乐观锁规则：先查后修改的任何操作，需将查询的版本号放入修改对象version值内。

数据库状态：每个状态都需要对应一个枚举类，并继承 implements IEnum<Integer> 来做返回类型和中文输出。

迭代版本，数据库表结构变动，请同时同步navicat协作组模型设计，并记录下修改sql，在对应后端开发需求备注里写上对应sql。



账号文档
-------------
https://cngongbao.yuque.com/docs/share/5c5284ac-0564-45fe-af59-751a3f8a58f2?#



开发环境（dev）：
-------------

接口文档地址：
http://gbwdev.gateway.hzcngb.com/doc.html
账户：gbw
密码：123456

工保网后台：
http://gbwdev.backstage.hzcngb.com/

工保网前台：
http://gbwdev.client.hzcngb.com/

工保金对接前台：
http://gbwdev.third-party.hzcngb.com

工保网环境网关：
http://gbwdev.gateway.hzcngb.com/

admin监控中心:
http://gbwtest.admin.hzcngb.com

保险公司回调地址：
外网：
http://t.cngongbao.com:11000/dev/gbw

工保网oss回调：
外网：http://t.cngongbao.com:11000/test/gbw/common/oss/callbac


测试环境（test）：
-------------

接口文档地址：
http://gbwtest.gateway.hzcngb.com/doc.html
账户：gbw
密码：123456

工保网后台：
http://gbwtest.backstage.hzcngb.com/

工保网前台：
http://gbwtest.client.hzcngb.com/

工保金对接前台：
http://gbwtest.third-party.hzcngb.com

前台外网映射地址：
http://t.cngongbao.com:10004/

工保网test环境网关：
http://gbwtest.gateway.hzcngb.com/

保险公司回调地址：
外网：
http://t.cngongbao.com:11000/test/gbw


工保网oss回调：
外网：http://t.cngongbao.com:11000/test/gbw/common/oss/callbac



预发环境（release）：
-------------

接口文档地址：
http://release-gateway.gongbao.cn/doc.html
账户：gbw
密码：123456

后台：
http://release-gbwbackstage.gongbao.cn/

前台：
http://release.gongbao.cn/

网关：
http://release-gateway.gongbao.cn/

admin监控中心：
http://release-admin.gongbao.cn

工保网oss回调：http://release-gateway.gongbao.cn/common/oss/callbac



正式环境（从库）
-------------

接口文档地址：
http://gateway.gongbao.cn/doc.html
账户：gbw
密码：123456

工保网生产环境前端地址：

后台：
http://gray-gbwbackstage.gongbao.cn/

前台：
http://gray.gongbao.cn/

网关：
http://gateway.gongbao.cn/

admin监控中心：
http://admin.gongbao.cn

工保网oss回调：http://gateway.gongbao.cn/common/oss/callbac



正式环境（主库）
-------------

接口文档地址：
http://gateway.gongbao.cn/doc.html
账户：gbw
密码：123456

工保网生产环境前端地址：

后台：
http://gbwbackstage.gongbao.cn/

前台：
http://www.gongbao.cn/

网关：
http://gateway.gongbao.cn/

admin监控中心：
http://admin.gongbao.cn

工保网oss回调：http://gateway.gongbao.cn/common/oss/callbac



crm客户关系管理系统
-------------

开发环境：http://gbwdev.crm.hzcngb.com


测试环境：http://gbwtest.crm.hzcngb.com


正式环境：http://crm.gongbao.cn



阿里云
-------------
开发账户：

账号：hz-appuser01@1308928102086701.onaliyun.com

密码：Queep&exohXah7%qJEfIsc

日志中心：

https://sls.console.aliyun.com/




测试环境监控中心
-------------

http://172.16.200.21:32001/login

账号：admin

密码123456