# RTP 与 B-FTP

RTP（Resource Transfer Protocol）负责资源批次控制；B-FTP（Binary File Transfer Protocol）是可选的数据传输协议。RTP 也可协商其他方式，例如
HTTP。服务端未配置 HTTP 时返回 `supportsHTTP=false`，客户端使用 B-FTP；协商返回 `supportsHTTP=true` 和有效 HTTP 地址时，客户端自动使用 HTTP 下载文件，不创建 B-FTP Worker。

将 `FTPClient.masterHandler()` 挂到外部 Master 通道的 SimpNet codec 后，调用 `runInit()`，等待返回的 CompletionStage 成功再调用 `requestFiles()`。初始化发送 `0x60`，服务端回复 `0x61`；同一连接重复调用 `runInit()` 复用协商结果。初始化等待最多 10 秒，断线或关闭时终止等待。不要在 Netty EventLoop 上阻塞等待。

服务端默认声明 1 个 Worker、65536 字节 MTU；客户端 B-FTP 实现使用 1 个 Worker，要求 MTU 为 65536，不兼容时初始化失败。HTTP 模式不受 B-FTP MTU 限制。未完成初始化的批次请求会被拒绝。Master 断线重连后须重新初始化。

## HTTP 升级接入

`FTPServer.startHttp(port, advertisedHost)` 在应用线程上启动可选 HTTP 服务，并声明 `http://host:实际端口/`；端口可为 0，由系统分配。HTTP 服务与 RTP 共享 CAS，随 FTPServer 关闭，并包含在 `termination()` 等待范围内。应在客户端调用 `runInit()` 前启动。

也可使用 `new FTPServer(storage, runningHttpServer, publicEndpoint)` 挂接已启动的 `HttpResourceServer`。两者必须共享同一个 CAS；HTTP 服务由调用方关闭。`publicEndpoint` 是客户端可访问的完整根地址，可经过反向代理，路径末尾的 `/` 可省略。不接受查询参数、片段或用户名密码。HTTP 服务已经停止时，新连接协商不再宣告 HTTP 能力。

升级后仍通过 Master 的 `0x62/0x63` 获取完整批次清单，缺失资源不发起 HTTP 请求；每个可用文件请求 `httpEndpoint + SHA-1`。客户端复用 common 中的 `RangeDownloader`，支持 Range 和普通 GET，兼容空文件。下载到独立临时文件，检查长度与 SHA-1 后原子提交缓存；进度、结果及 `0x64` 批次释放与 B-FTP 一致。

HTTP 失败按下载器策略重试，最终失败上报批次异常，不自动回退 B-FTP。取消、Master 断开或客户端关闭会中断 HTTP 下载，并在 IO 停止后清理临时文件；HTTP 下载连续两分钟无进展会超时。失败的文件不会替换已有缓存，已提交的其他文件保留。`FTPClient.close()` 非阻塞；在应用线程等待 `FTPClient.termination()` 可确认活动批次清理和自有资源关闭完成，外部 Master 仍由调用方关闭。

包 ID 为线上 ID。RTP 控制包走 Master 通道；B-FTP 包走独立二进制 Worker 通道。

## RTP

| ID   | 包                    | 方向与行为                        | 字段                                                                                                  |
|------|----------------------|------------------------------|-----------------------------------------------------------------------------------------------------|
| 0x60 | P60_RTPProtocolInit  | 客户端发起 RTP 初始化，服务端回复能力。 | 无                                                                                                   |
| 0x61 | P61_RTPProtocolInfo  | 服务端返回能力；客户端优先 HTTP，否则校验 MTU 使用 B-FTP。       | `maxWorker` 最大 Worker 数；`transportMTU` 传输 MTU；`supportsHTTP` 是否支持 HTTP；`httpEndpoint` HTTP 地址。      |
| 0x62 | P62_RTPBatchRequest  | 客户端请求一批资源，最多 32 个且不可重复。      | `batchId` 批次标识；`resourceIds` 小写 SHA-1 列表。                                                           |
| 0x63 | P63_RTPBatchResponse | 服务端在传数据前返回完整结果。              | `batchId`；`files` 可提供资源（`resourceId`、`taskId`、`size`、`sha1`）；`missingIds` 缺失资源；`rejected` 是否拒绝整个批次。 |
| 0x64 | P64_RTPBatchRelease  | 客户端释放批次及服务端资源句柄。             | `batchId`。                                                                                          |

## B-FTP

| ID   | 包                    | 方向与行为                       | 字段                                                           |
|------|----------------------|-----------------------------|--------------------------------------------------------------|
| 0x70 | P70_FTPRangeRequest  | 接收端请求一个分片。                  | `taskId` 任务标识；`partId` 分片编号，偏移为 `partId × 65536`。            |
| 0x71 | P71_FTPInlineRequest | 接收端请求以单包发送小文件（不超过 16 KiB）。  | `taskId`。                                                    |
| 0x72 | P72_FTPPayload       | 发送端返回所请求的完整内容或分片；最大 64 KiB。 | `taskId`；`partId`；`data` 字节内容。INLINE 使用编号 0，空文件也发送空 Payload。 |
| 0x73 | P73_FTPCancel        | 请求接收端取消任务。                  | `taskId`。                                                    |
| 0x74 | P74_FTPStatusRequest | 查询接收端任务状态；未结束时订阅最终结果。       | `taskId`。                                                    |
| 0x75 | P75_FTPStatusResult  | 接收端返回或上报任务状态。               | `taskId`；`status`：接收中、校验中、提交中、完成、取消、超时、失败或未知。                |

RANGED 文件由接收端按窗口请求固定 64 KiB 分片，可乱序接收；只有完整接收并通过 SHA-1 校验后才提交文件。单个 Worker
断开不代表任务完成。缺失资源只列入 RTP 响应，不传输。

## 基础系统测试

运行 `./gradlew :mcu-server:test`（Windows 使用 `./gradlew.bat :mcu-server:test`）。
`src/test/java/org/atcgroup/mcupdater/server/v4/rtp/ResourceTransferBatchTest.java` 使用真实本地 TCP 和 SimpNet codec，覆盖初始化、未配置 HTTP 时的 B-FTP、不兼容 MTU、大小文件与空文件、缺失资源、进度、连续批次和 CAS 损坏时拒绝传输。HTTP 升级测试覆盖真实 HTTP 连接、Range 下载、空文件、缺失资源、连续批次、保持 Master 连接且不创建 Worker、HTTP 模式忽略 B-FTP MTU、自有与外部 HTTP 服务归属、HTTP 端点失效、SHA-1 校验失败后保留已有缓存，以及取消和 Master 断线后中断下载、清理临时文件。
