# 已入库的车辆字段与信号

用 get_asset 取得实际部位列表，按用户问题取证；不要把下面的候选字段都当作每台车都有的数据。
VEHICLE 部位的登记属性可查 registeredManufacturer、registeredModel、modelYear、market、softwareVersion、vehicleAlias、commissionDate。
这些是原包模拟登记声明，不是当前实车身份验证。softwareVersion 与部分证据中的 software_version 是不同属性，按实际返回值使用。

充电设置配置候选：CHARGE_PORT 的 schedule_enabled、scheduled_start_at、schedule_start_local、site_timezone、soc_pct、charge_limit_pct、charging_state。
配置缺失时查询对应观测，不能以“没有配置”代替“没有状态”。
观测 signal 由部位和字段组成，如 CHARGE_PORT.charge_port_color、CHARGE_PORT.light_pattern、CHARGE_PORT.charging_state、CHARGE_PORT.operator_statement。
按关键信号查询颜色、常亮/闪烁、状态及身份绑定；不要只取默认最近 12 条就声称覆盖全部观测。
身份辅助信号包括 CHARGE_PORT.identity_binding、CHARGE_PORT.observed_vehicle_alias，来源若指向别车应列为身份问题。

胎压/轮胎候选：TIRE-LF.measured_pressures_kpa、TIRE-LF.label_target_kpa、TIRE-LF.cold_soak_hours、TIRE-LF.tpms_flash_duration_s、TIRE-LF.sensor_packet_missing、TIRE-LF.sensor_ids。
某些 value 为 JSON 文本，保留轮位、单位和原字段含义。不要把字典整体当一个压力值；单位换算需说明。
滤芯、雨刮、制动分别查询 get_asset 返回的 CABIN_FILTER、WIPER、BRAKES 部位与对应精确信号。

供电/风险候选：ICCU.warning_text、ICCU.reduced_power_reported、ICCU.applicability_status、ICCU.campaign_id、ICCU.market、ICCU.query_mode；低压、动力电池、BMS 的信号按实际部位与返回范围查询。
这些召回相关记录为模拟响应，不是真实 VIN 查询。当前工具不提供外部召回服务，不把模拟别名用于真实查询。

维护历史使用 get_maintenance_history；保留实际返回的 operation、beforeValue、afterValue、sourceId 与 validFrom。动作回执不等于恢复验证。
