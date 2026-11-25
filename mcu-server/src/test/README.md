# 已有测试范围

覆盖固定 MTU、BitSet、多 Channel 调度、协议编解码、Hash、取消/超时、资源清理和真实 TCP 收发。
批次测试覆盖服务端解析、传输前缺失项回调、可提供文件全部提交、全缺失、快照与连接复用。
传输测试已迁移到 `mcu-common`，包名为 `me.gb2022.mcupdater.rtp`。

版本测试保留在 `mcu-server`：`VersionInfo` 验证 JSON 清单、资源快照和操作执行；
二进制往返、重复资源键和非法集合计数改为验证 `UpdateHeader`，P13 验证其更新头载荷。
提取和删除项按更新头的 Set 语义比较，版本摘要使用 `CompiledVersionData`。
更新头暂未提供从清单构建的接口，测试直接构造线格式夹具，不依赖尚未完成的新系统。

```powershell
.\gradlew.bat :mcu-common:test --tests 'me.gb2022.mcupdater.rtp.*'
.\gradlew.bat :mcu-server:test --tests 'org.atcgroup.mcupdater.server.v4.*'
```

本轮仅调整已有测试和迁移后的测试配置，未新增测试、未运行测试；不包含性能基准。
