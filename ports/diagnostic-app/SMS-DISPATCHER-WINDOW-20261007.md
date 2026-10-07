# 0.9.10：查询时的短信分发器与通话后短信证据

本版增加只读诊断，不修改两个替换引擎。它分别显示系统 `ISms` 支持查询、
独立 MMTEL 注册/能力回调、电话进程短信分发器的查询时间窗采样。

## 实际 ROM 的语义

当前 MIUI Android11 的框架快照已与手机文件 SHA-256 对齐：

```text
telephony-common.jar 6cc255f3cd8fe8f11191a1d2ec0bfddfdccf31d3851cdb3cd9282564871c3f74
ims-common.jar       fad990f14770110e52f10dbe9b6e431e3bd33a4bea42a1c511139c73b11fdbe6
```

该快照中，`IccSmsInterfaceManager.isImsSmsSupported()` 转交 controller 的
`isIms()`。controller 首先判断软件 `ImsSmsDispatcher.isAvailable()`，否则
可返回旧式 radio IMS 的 `mIms`。发送文本则独立判断软件 dispatcher 的
`isAvailable()`/紧急短信支持，选择 IMS 或 GSM/CDMA。因此查询 boolean
不能替代软件 dispatcher 的就绪证据。

软件 dispatcher 需要 `mIsImsServiceUp && mIsRegistered && mIsSmsCapable`。
其 `isAvailable()` 在这份 ROM 中记录三项状态，支持查询本身就会产生该日志。
旧界面称其为“最近发送时”不够准确，本版改为“最近状态日志”，说明它也可能
来自支持查询。原生发送还存在 `ImsException` 后 fallback 的路径；仅凭103或
IMS TX0 不能认定完整选路。

## 新采样和隐私边界

仅 API30/raphael 且上述 telephony framework 哈希命中时，采集本次 Binder
查询起止 wall-clock 时间内、选定 slot 和当前 phone PID 的布尔日志。查询
时长与 elapsed 时间不一致、身份变化、无样本、状态冲突或超过64个样本都
保持未知。查询前后重新核对 slot/sub。其他 ROM 显示未校准，不推断同样语义。

日志逐行处理，只保留布尔、样本数和固定原因。应用不导出原日志、PID、
进程启动时间、短信正文或认证资料。采样不会发送短信，也不保证下一条成功。
31项主机契约覆盖时间边界、错误PID/槽、冲突、溢出、身份变化和导出范围。

## 本轮业务测试及保留失败

使用既有0.9.9引擎执行一次191通话，接通、框架 active、发送300帧、交给播放291帧，
挂断/BYE200。随后向85075发送一次INFO。通话和短信之间没有 reload；电话身份、
原owner及IMS代次保持。短信 TX1/OK1、RX4/ACK4、失败0，合并入库1条且出现通知。
四次查询窗采样均为 up/reg/SMS=true。尚未确认人的语音听感。

初版测试采样器遗漏结果日志的固定 `slot=1 sub=1` 后缀，导致回调结果列表空、
总报告 failed。保留该报告，通过只读历史时间窗补采唯一 RESULT_OK 回调，另存
通过的补充证据，没有重发短信。短码策略原值为未设置，所有业务与状态观测
完成后才 reload 清理权限；不能把清理后的状态用于前面业务成功的证明。

随后安装0.9.10成功，但要求短信业务就绪的实机断言 failed。补采揭示：独立
MMTEL WLAN/语音/SMS=true，电话分发器 up=true/reg=true/**SMS=false**，系统
ISms支持=false。新版诊断准确显示了这个不一致，不能把安装成功或模拟器绿灯
解释为当前短信业务恢复。

再调用已安装服务的按槽注册刷新函数，REGISTER发送计数增加1、最近响应200，
电话与IMS身份保持、无短信流量，但六次采样的分发器SMS仍false。这次修复尝试
**未恢复**短信，原结果保留。单纯重新注册和现有两秒能力回放均不能据此视为
完整修复；仍需核实 phone capability callback 的绑定与重连。

## 载荷与版本范围

签名应用0.9.10-diagnostic / versionCode22：

```text
51b76628e08ab61955c951ac05ced48e2ddfc78f8a21632e5cc7f6091529c926
```

API30引擎、现代引擎分别原样保留0.9.9的 `bedcea...1f9cdd`、`ffb955...54705a`。
原冻结0.9.6产物未覆盖。首个主机构建因测试计数门槛与实际29项不一致失败；
补充零毫秒和时间窗末端的必要边界案例后，新独立批次31项及集中构建通过，
原失败批次保留。这是测试基建修正，不是引擎修复。

签名、嵌入引擎逐字节身份、源代码重新构建和集中主机检查通过。Android13/
API33 与 Android16/API36 两个实际受管模拟器并行通过诊断与拒绝检查；未校准
ROM 的新字段保持 unknown，未创建生产状态，两个进程均终止。没有现代运营商
通话/短信或双活真实SIM证明。Android17原fixture恢复、长时间稳定性和完整
Android11–17真实业务适配仍未完成。

公开元数据见 [本轮报告](reports/20261007-sms-dispatcher-window/summary.json)。
