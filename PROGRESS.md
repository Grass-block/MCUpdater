# 当前进度（2026-11-23）

## 工作约定

- 新系统尚未完成：不新增测试、不运行测试，除非用户后续明确授权。已授权随协议变更调整已有测试。
- 用户当前关注 `me.gb2022.mcupdater` 下的新协议和新客户端；旧包仍被部分代码复用，不代表整体迁移完成。
- 工作区有大量用户原有、暂存和未提交改动。不得整体还原、覆盖或擅自提交；继续工作前读取当前文件，用户可能同时修改。
- 本次最新任务仅整理进度文件，不继续修改功能。

## 当前结构与协议

- RTP、存储和数据类已迁到 `mcu-common/src/main/java/me/gb2022/mcupdater`，传输入口现名为 `RTPClient`、`RTPServer`。
- MCUProtocol 注册 0x01/0x02 握手、0x10/0x11 频道信息、0x12/0x13 更新请求与更新头，并注册 RTP/B-FTP 包。
- `VersionInfo` 负责 JSON 清单：channel/version/timestamp/assets/extract/update/delete；assets 为资源键到 provider=rtp/http 的映射，HTTP 资源带 url 和 long length。
- 二进制更新载荷现在由 `UpdateHeader` 承担，P13 携带该更新头；其中版本摘要复用旧包的 `CompiledVersionData`，extract/delete 为 Set，assets/update 为只读 Map。
- 新频道 `ChannelMetadata` 的 name/description 已为 `TranslatableComponent`，构造器、getter 和二进制读写均使用组件。`ChannelData` 配置加载已修正 Bukkit 配置节点的读取，支持语言映射、普通文本及省略描述。
- `OperationResolver` 仍用于本地 CAS 的删除、解压、覆盖；新客户端有独立的文件应用流程。

## 传输能力与接入方式

- P60/P61 初始化，协商成功后才能请求批次；初始化超时 10 秒。B-FTP 使用 65536 字节 MTU、一个 Worker。
- P62/P63/P64 提供批次请求、完整可用/缺失清单和释放；每批上限仍为 `P62_RTPBatchRequest.MAX_FILES = 32`。
- B-FTP 支持按固定分片请求、小文件/空文件 inline、SHA-1 校验、提交、取消、超时和资源清理。
- RTP 可协商 HTTP：仍先获取批次清单，再按 endpoint + SHA-1 下载；独立暂存、长度/Hash 校验、原子提交，不创建 B-FTP Worker。HTTP 失败不自动降级 B-FTP。
- `RTPServer.startHttp` 可启动自有 HTTP 服务，也可挂接共享 CAS 的外部 HTTP 服务；外部服务生命周期由调用方管理。
- 已按用户要求核对：CDN 下载侧使用 `RTPClient.runStandalone` 建立独立连接；源站下载侧把 `masterHandler()` 挂到现有连接管线。两种入口原本就存在，本轮保留其设计。
- `runStandalone` 已补连接处理器就绪等待、连接失败时 EventLoop 清理，以及返回句柄关闭连接和 EventLoop 的行为。
- 源站挂载/移除 handler 在 EventLoop 上执行并等待，CDN 和源站均等待 `runInit()` 完成再请求资源；异常时也移除源站 handler。

## 本轮已完成的修复（审查第 1–9 项）

1. P02_ServerHandshake 增加 ByteBuf 反序列化构造器。
2. 资源解析先复制只读资源集合，在副本上移除已解析项；仅剩余未知资源时抛错，空集合不再必然失败。
3. 更新下载和应用移到独立串行工作线程，不在 Netty 收包线程中阻塞等待。
4. 修复 RTP 初始化、挂载就绪等待及异常清理，详见上述接入方式。
5. 客户端握手回调保存 serverInfo，字段使用 volatile 供工作线程读取。
6. 移除 HTTP 下载完成后的空 AtomicReference 访问；Hash/长度不匹配最多下载校验 3 轮，失败抛 IOException，不再无限递归。
7. HTTP/CDN/源站下载异常向 TaskBatch 传播；源站最终缺失项视为失败，下载失败不进入文件应用阶段。TaskBatch 不再依赖旧客户端线程池，使用自有有界并发线程池并在退出时取消任务、关闭线程池。
8. 删除文件改为移动至 `.updater/removed/原相对路径`，创建备份父目录，避免只用 basename 导致冲突；不再将 ATOMIC_MOVE 用于 Files.copy。
9. 删除/覆盖路径先规范化并检查运行目录边界，拒绝替换根目录和通过已有链接跳出根目录；应用前检查操作路径和资源 SHA-1。覆盖失败抛异常，不再只打印日志后继续。

## RTP 顺序拆批与进度

- 用户后续授权修复第 10 项：`AssetDownloadResolver.RTPDownloadResolver.downloadBatches` 按 MAX_FILES 顺序请求；一次更新资源总量不再受 32 项限制。
- 每个阶段复用已协商的同一个 RTPClient/Master 连接；前批结束后才发下一批。
- CDN 阶段汇总各批缺失项，再交源站阶段拆批补齐；源站汇总后若仍有缺失则失败。
- 保留“正在下载文件(1/2) / (2/2)”或无 CDN 的原标题，以及阶段内累计已接收字节显示。
- 单批仍按字节上报；多批按资源数量分配固定进度区间，在当前批内按字节插值，避免每批归零。后续批次字节数直到收到清单才知道，因此显示总字节是当前已知累计值，多批百分比不是整个阶段精确的字节占比。
- `TaskBatch.Task.reportProgress` 当前仍为空：拆批逻辑已生成汇总回调参数，但新 UI 的实际进度呈现尚未接通，不能声称已验证界面效果。

## 已有测试调整

- 仅修改已有测试，没有新增测试方法或测试类。
- `VersionInfoCodecTest` 保留 JSON、资源快照检查；二进制往返、重复资源键与非法集合计数改测 UpdateHeader，P13 改测 getHeader；Set 按集合语义比较。
- UpdateHeader 尚无从清单构建的公开接口，测试直接构造线格式夹具。
- 传输测试现位于 `mcu-common/src/test/java`，恢复包名 `me.gb2022.mcupdater.rtp`，修正失效导入及静态导入。
- mcu-common 测试配置启用 JUnit Platform，并让测试继承生产 compileOnly 依赖以获取 Netty、日志和 RocksDB。
- `mcu-server/src/test/README.md` 已更新范围、迁移位置和命令。

## 尚未闭合／后续核对

- 最新读取时，用户已继续补上 requestUpdate(P12)、requestConfig(P10)、握手后按 UI 返回值决定更新，DesktopClientUI 已改为 extends ClientUI。不要再把此前的 `new ;` 和 implements 抽象类问题当作现状。
- 新客户端 `completeUpdate(UpdateHeader)` 仍为空：版本时间保存、成功回调、连接关闭和启动锁恢复需要继续接通。
- 最新 ClientUI 已无 start 方法，但 MCUpdaterClient.start 仍调用 clientUi.start(this)；客户端引用绑定 `_init`、频道配置处理及 UI 流程需要按当前代码重新核对，不应声称完整客户端可编译。
- P11_ChannelHeaders 仍无公开填充/读取频道集合的 API，频道数量与 TranslatableComponent 数量仍使用有符号 byte，128 项以上边界未处理。
- 新服务端 `me.gb2022.mcupdater.server.MCUpdaterServer` 仍为空；RTPServer 提供 masterHandler/workerHandler，Worker 与 Master 的外部关联接入尚待完成。不要把已有 runStandalone 和管线挂载入口误报为不存在。
- RTPServer/FileReceiver 生产代码仍有 `lab.starfish.mcupdater.rtp.protocol.*` 残留导入，未在本轮授权的修复中清理。
- UpdateHeader 的版本数量和嵌套摘要计数等边界仍需审查；VersionInfo.encode 当前为空，不应再将其当成完整二进制 API。
- 本轮路径检查覆盖删除/覆盖目标与资源键；解压仍调用旧 PatchFile.unzip，包内路径安全不在这次修复覆盖内。
- 本轮拆批及客户端修复没有端到端运行验证；不要据静态检查推断完整更新链路已可用。

## 验证记录与历史结果

- 本轮修改做过定向 git diff --check，无本轮文件的空白错误。
- P02_ServerHandshake、TaskBatch 使用 `javac --release 17`、现有 productions/mc-updater-server.jar 作为 classpath 定向编译通过，输出在临时目录；不是完整模块编译。
- 其余客户端修复、RTPClient 就绪清理和拆批进度仅静态检查，未新增或运行测试，未完整构建，未提交 Git。
- 历史：用户提供过 61 项 passed 的测试导出，未再出现 event executor terminated；那是用户执行且早于本轮迁移、协议变更和客户端修复，不能代表当前版本通过。
- 历史关闭修复保留 shutdownStarted、防止重复关闭、termination 上报；RangeDownloader 已修复取消/中断重试警告和 Probe 3 次尝试计数。
- 主动取消、断线、关闭 HTTP 端点的测试可能产生预期错误日志；用户曾要求先保持现状，不继续扩大排查。
- 历史 Gradle 存在缓存锁权限及依赖解析阻碍，没有确认当前完整构建可用。
- 旧测试导出位于根目录 `测试结果 -'MCUpdater_mcu-server_test'_中的测试.xml`，可能被用户后续覆盖。
