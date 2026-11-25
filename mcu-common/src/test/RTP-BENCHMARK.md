# RTP 本地传输性能基准

这是独立入口，不是 JUnit 测试；普通 test/check/build 不会启动传输。当前代码尚未编译或运行。

从仓库根目录启动，默认 `bftp` mode：

```powershell
.\gradlew.bat :mcu-common:rtpBenchmark
```

HTTP mode 只需选择一个 mode：

```powershell
.\gradlew.bat :mcu-common:rtpBenchmark -PrtpBenchmarkMode=http
```

Gradle 任务运行目录是 `mcu-common`。数据及报告会写入该运行目录下的 `rtp-benchmark` 子目录，并为每次运行创建一个独立目录。也可在 IDE 直接运行 `me.gb2022.mcupdater.server.test.BenchmarkClient.main`，它仅接收可选的一个 `bftp`/`http` 参数，缺省 `bftp`。`BenchmarkServer.main` 是独立服务端入口，使用服务端 JVM 的当前运行目录；正常运行由客户端启动独立服务端 JVM。

服务端和客户端是两个 JVM，通过真实 TCP 连接本机 loopback 随机端口。服务端单独打开源 CAS，客户端使用独立下载目录。服务端日志保存为 `server.log`。使用生产 RTPClient、RTPServer、CAS 和接收器；仅 Master/Worker 关联由本地服务端夹具完成。

## 数据集

- 精确 16 GiB（17,179,869,184 字节），256 个文件，平均精确 64 MiB。
- 文件大小在 0–256 MiB 之间，包含三个空文件和一个 256 MiB 文件。其余文件按每四个随机分割 256 MiB 生成，再打乱顺序；这是有界随机分布，不是均匀分布。
- 固定本次运行生成的种子伪随机字节，分块生成，不会把整个数据集放进内存。
- 每次使用新 CAS 和下载目录，避免下载缓存命中；空文件在 CAS 中自然去重，仍按三个文件请求计数。
- 每个文件单独一个批次，复用 Master 连接，无人为间隔连续提交。大文件按生产 B-FTP 协议分块传输。这避免为单个清单预先哈希 16 GiB 时触发清单超时。

## 计时和报告

随机文件生成、导入 CAS、启动服务端进程、建立 Master 连接及初始协商不计时。正式传输计时包含每批请求、清单、Worker 建连、文件读写、生产接收端 SHA-1 校验及提交，以及少量 CSV 写入。进度回调约每 5 秒显示累计和区间速度。

传输完成后会再次独立检查所有结果文件的长度和 SHA-1，复核时间不计入测速。复核通过才生成 PASS 汇总。

每次运行目录包括：

| 文件/目录 | 内容 |
|---|---|
| environment.txt | mode、JVM/OS、处理器数、开始时间 |
| dataset.csv | 数据集文件序号、SHA-1、大小 |
| files.csv | 每文件耗时和 MiB/s |
| summary.txt | 状态、字节数、耗时、MiB/s 和十进制 Mbit/s |
| server.log | 服务端 JVM 输出 |
| cas/、downloads/ | 源 CAS 和接收结果，运行后保留 |

至少需要 33.25 GiB 可用空间，运行后保留约 32 GiB 数据。清理时删除对应的单次运行目录即可。

这是本地端到端应用吞吐量，包含磁盘、哈希、操作系统页缓存、两端 JVM 的 CPU/GC 竞争及协议开销，不能视为纯网络带宽。未强制清空系统缓存，也不包含预热传输。
