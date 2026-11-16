# 已入库的工业设备字段与信号

先用 get_asset 确认部位；SYSTEM 常见登记属性为 registeredManufacturer、registeredModel、registeredSerialNumber、productFamily、orderNumber、firmwareVersion、engineeringVersion。
不是每台设备都有全部属性，登记的型号不能证明实体已核实。
PN-01 等通信部位的配置候选为 expected_profinet_name、configured_device_name、device_name、expected_station_id；CPU/控制部位可有 configuration_control_enabled；CTRL-01 等部位可有 installed_part。
部位与属性按 get_asset 和实际工具结果确定，不将所有设备写成同一槽位。

观测 signal 为部位加原字段，如 PN-01.online_profinet_name、PN-01.io_data_exchange、PN-01.link_up、PN-01.hmi_value_updates、PN-01.identity_binding、PN-01.observed_mac、PN-01.registered_mac。
默认最多取最近 12 条；必要时按具体 signal 查询，不能把默认窗口当完整诊断缓冲区。
诊断返回中的 instruction、laddr_type、mode、return_code_hex 等字段也按其实际部位前缀查询；未取得字段不补造厂商状态。

维修历史可包含命名、替换和批准声明。优先核对 sourceId、validFrom、beforeValue/afterValue，与观察到的结果分别引用。
导入时没有将多个反事实模拟世界合并；其他场景文件与评测答案不属于模型可见证据，不从文件路径自行读取。
