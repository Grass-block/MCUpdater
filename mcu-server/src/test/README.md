# 传输测试

覆盖固定 MTU、BitSet、多 Channel 调度、协议编解码、Hash、取消/超时、资源清理和真实 TCP 收发。
批次测试覆盖服务端解析、传输前缺失项回调、可提供文件全部提交、全缺失、快照与连接复用。

```powershell
.\gradlew.bat :mcu-server:test --tests 'org.atcgroup.mcupdater.server.v4.protocol.*'
```

本轮新增测试尚未运行；不包含性能基准。
