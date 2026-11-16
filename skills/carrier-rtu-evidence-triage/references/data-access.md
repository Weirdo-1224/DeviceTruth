# 当前公开登记快照字段

本批公开资产使用整机逻辑部位 ASSET；它不表示已获得实际部件 BOM。get_asset 只返回登记 ID 与部位清单，型号和序列号需要进一步查询属性。
get_configuration 的 componentId 使用 ASSET，attribute 按任务选择 registryManufacturerSnapshot、registryModelSnapshot、registrySerialNumberSnapshot、registryDescriptionSnapshot、registryCommissionDateTextSnapshot、registryStatusCodeSnapshot。
属性中的 Snapshot 表示来源系统采集时的登记文本；保留原始值，不将其当作现场铭牌核验或当前设备性能。
validFrom 是公开文档的采集时刻，不能作为物理投运日；投运日期文本需核对原始口径。当前库没有自动合并采购附件里的另一日期，未检索到附件时不声称已比较两份资料。

get_maintenance_history 返回 WORK_ORDER_DOCUMENT_SNAPSHOT；sourceRef 中包含原始工单定位、描述和登记状态，beforeValue/afterValue 为空。
可以说“工单文档记载某项工作”，不能说“已经完成更换并验收恢复”。未查到工单不代表从未维修。

Carrier 规则只用于型号核对后有系列交集的设备；不能推广到另外厂商或所有 NYC-AMPS 设备。
本 Skill 不声明 get_observations：当前观测表只支持模拟数据，没有该真实机组的在线读数。
手册、过滤器参数和适用性需要通过实际成功的知识检索取得；原包 PUB/SUP 编号不能直接作为本次引用。
