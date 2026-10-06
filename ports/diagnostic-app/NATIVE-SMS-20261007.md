# 0.9.6：系统短信服务状态与本轮实机证据

## 修改

诊断应用现在分别显示 MMTEL 的 SMS 能力和系统短信服务针对所选订阅的
`ISms.isImsSmsSupportedForSubscriber` 返回值。查询后重新检查活动订阅列表，
确保 slot/sub 没有变化；服务缺失、异常和订阅变化均报告未能观测。
新增检查受现有 32 秒 watchdog 约束，本身不发送短信。

两项结果不一致时，界面提示在空闲状态重新拉起后复查。支持状态只代表
检查时系统上报的能力，实际发送、接收、原生入库和通知必须分别验证。

初版候选在 MIUI `app_process` 中用单槽便捷订阅查询，出现
`NullPointerException`。最终候选改用诊断已经验证可用的活动订阅列表；
两槽 root 检查通过。保留候选失败，未将它计作通过。

## 实机结果

本轮使用既有完整 API30 IWLAN/QNS/IMS 替换引擎，组件 mask 为7。
测试前核对三个安装 APK、controller 和 helper 与冻结模块内载荷逐字节一致。
没有改动 APN、vendor、modem 或服务载荷。

| 检查 | 结果及边界 |
| --- | --- |
| 第一次 INFO→85075 | 系统发送回调103，替代 IMS TX0，未收到回复；原短码策略恢复 |
| 旧电话进程日志 | 曾上报 up=true、reg=true、sms=false；仅为历史进程证据 |
| 清理重载后系统 Binder | 所选 SIM 报告支持 IMS 短信 |
| 后续 INFO→85075 | TX1/OK1、RX4/ACK4、失败0，系统合并入库1条，Messages 通知出现；原短码策略恢复 |
| 新版实机 root 诊断 | 活动槽 WLAN 注册、语音/SMS 能力、系统短信支持均可见；空槽正常结束 |
| 随后的旧191回归 | 未证明接通或媒体，最终通话空闲 |
| 补齐就绪检查后的191 | 连续两次 WLAN 语音就绪；CALL 入口成功；SIP 100/183/200/488，无 CONNECTED、发送/播放帧0，最终通话空闲 |

103 对应 Android 的 `RESULT_RIL_INVALID_STATE`，表示 RIL 在不适合处理
该请求的状态。[Android 官方 API 定义](https://developer.android.com/reference/android/telephony/SmsManager#RESULT_RIL_INVALID_STATE)
零 IMS TX 和旧 SMS 能力为false，与系统未走替代 IMS 发送入口相符，
但这仍是路径推断，未证明当次 framework/RIL 的完整选择过程。

语音状态日志按 Call-ID 记录所有响应，现有版本没有记录 CSeq 方法。
因此 200 可能属于 UPDATE/PRACK，不能将它计为 INVITE 接通；488 的具体
请求归属和拒绝原因仍须查明。代码检索未发现本地生成488的路径。

## 版本与并行验证

最终签名 APK：`0.9.6-diagnostic` / versionCode18，SHA-256：

```text
88097e2715461607a511b89549fbc00dc03388d9378f9dd517bd66ddf7bef0cc
```

已安装并核对实机包版本、哈希；空槽和活动槽并发 root 诊断通过。
Android13/API33 与 Android16/API36 同时运行只读诊断、非root/参数错误/
缺模块/缺owner的操作拒绝检查，通过后核实两个受管模拟器进程终止。
两个 guest 均实际取得系统短信 Binder 返回的 boolean；这不是运营商短信测试。

API30 模块、现代模块及其 helper 保持此前冻结身份。集中主机契约检查在初版
候选构建时通过；最终修正另做构建、签名、嵌入引擎身份、实机及上述模拟器检查。
应用自身 UID 的完整界面操作仍受实机锁屏影响，未计为通过。

API33 原始恢复记录与41阶段生命周期验证见
[专门报告](RECORDED-RECOVERY-API33-20261007.md)；这与新版应用的只读 smoke
分开记录。Android17 原保留 fixture 恢复、现代真实运营商业务、双活真实SIM
以及本轮191媒体验证尚未完成。

公开证据位于 [reports/20261007-native-sms](reports/20261007-native-sms/summary.json)，
仅含状态、计数和载荷身份，不包含短信正文、用户账户、完整 SIP/SDP 或原始电话日志。
