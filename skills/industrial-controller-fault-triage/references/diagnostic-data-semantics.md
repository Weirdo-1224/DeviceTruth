# 诊断调用、覆盖与时间

DeviceStates（Siemens S7-1200 V20 正文，KS-003）：LADDR 使用 HW_IOSYSTEM；MODE 4 表示存在，MODE 5 表示问题位。16#8092 表示 LADDR 未指向 IO 系统，不能作为物理模块损坏证据。

16#8452 表示缓冲区不足，可以含部分数据。保留 covered_station_numbers、target_station_number、MODE 和 state_bits：覆盖内的目标位可按 MODE 解读，覆盖外目标保持未知。API 分页的 PARTIAL 与设备指令的部分结果是不同层次的覆盖信息。

原始消息和字段是项目模拟采集格式，不是 Siemens CSV 格式承诺。没有厂商事件码时 vendor_event_code=null；16#8092/8452 是模拟调用中使用的真实指令条件码。

诊断缓冲区有覆盖范围（KS-006），CSV 是保存时快照且有读取权限（KS-007）。日志为空、权限不足、参数无效、结果不完整分别报告。

时钟核查依据 KS-005。effective_at 为事实时点，captured_at 为采集时间，recorded_at 为入库时间。clock_quality=UNSYNCHRONIZED 时保留 device_timestamp，effective_at 可为 null；不能把漂移日期当成未来真实事件，也不能由窗口外未见记录证明事件未发生。

不同系列/固件、手册版本的容量和保留机制各自核实，不声明系列统一容量。工程摘要与连接模块身份分开（KS-009）。
