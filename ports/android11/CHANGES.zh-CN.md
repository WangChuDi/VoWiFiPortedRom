# Android 11 移植修改清单

基线：phhusson/ims `c180bdff810880d8f75f5dda70e6bcbee6991e9e`。
方案参考：[Suiying6023/VoWiFiPortedRom](https://github.com/Suiying6023/VoWiFiPortedRom/blob/main/PORTING-GUIDE.md)。
实机：raphael / MIUI V12.5.1.0.RFKMIXM / Android11 / VOXI23415 / 第二卡槽。

## 当前成果

已做成Magisk常驻接收试验模块：应用侧IMS注册、接收普通单条短信、交给默认短信应用入库和发通知、保存确认后回复RP-ACK。原厂IMS仍负责语音，未替换系统APK/vendor/基带。**不是完整的IMS APK移植；短信发送仍未修复。**

## 1. vendor/phhusson-ims/app/：API30兼容与短信协议修正

| 文件 | 修改 |
|---|---|
| PhhImsConfig.kt | 去掉API30不存在且原实现为空的RCS回调/类型 |
| PhhImsService.kt | 使用API30的receiver注册方式，要求发送方具备MODIFY_PHONE_STATE |
| PhhMmTelFeature.kt | 将实际slotId传入SipHandler |
| SipHandler.kt | 按卡槽绑定订阅与IMS网络；去除不可用的新版IMS媒体路径；兼容旧SmsManager；修正语法；短信SIP/RP确认状态与超时 |
| PhhImsSms.kt | 不再打印SMSC，异常时报告发送失败 |
| Sms.kt | 正确解码RP-ERROR长度/原因字段 |
| 新增SmsAddress.kt | SMSC支持MIUI AT格式及号码规范化、PSI校验、RP原因解析 |
| 新增SmsSubmission.kt | SIP接受与RP接受都满足才成功，失败/超时只完成一次 |

这些核心改动可在API30编译，但完整APK、原生媒体与语音替代路径没有实机验证。Gradle工程不因此成为可直接刷入的Android11 IMS服务。

## 2. ports/android11：已验证的独立接收实现

- `ImsApi30Probe`：指定SIM/IMS网络入口；常驻模式记录自己的PID。
- `ImsRegistrationProbe` / `ImsAuthenticatedProbe`：真实USIM AKA、受保护REGISTER和单Contact注销；不打印密钥；保留重复身份、Contact、路由及折行。
- `ImsNestedPolicy` / `ImsPolicyBroker` / `nested-policy.c`：仅对本进程传入socket设置内层transport＋原厂外层tunnel模板，解决已验证的XfrmInTmplMismatch；不修改全局XFRM策略。
- `ImsSmsProbe`：接收、解码、查重、默认SMS角色和受保护接收器核验；投递原始PDU和正确订阅；等待收件箱记录，再发RP-ACK并核对其SIP响应。
- 收件箱最初是直接插入，无法触发通知。当前0.2.0已改为SMS_DELIVER，由Google Messages入库和通知；同时取消模块运行时临时放开WRITE_SMS。
- 公开injectSmsPdu在本机遇到`not class 1`限制，因此未采用。没有通过改DCS或正文绕过这项检查。
- 读帧支持半包超时、二进制内容、折行、连续保活；ACK等待期间暂存其它帧，接收完一条后继续常驻。

## 3. Magisk生命周期

- 仅匹配raphael/API30，开机完成后延迟启动；无post-fs-data阻塞脚本、系统覆盖或SELinux规则。
- UID1000加inet组，确保fwmarkd实际完成IMS网络绑定；一次性root辅助程序仅处理传入FD。
- 每轮最长15分钟，保守考虑网络租期；正常注销后重连，失败最多退避10分钟。
- 暂停/恢复/状态命令和Magisk Action；禁用模块时请求退出；清理临时IPsec。
- 日志只保留当前/上一轮及有限事件，正常模块模式不输出短信正文。

## 4. 验证与限制

实测REGISTER200、注销200、短信解码和收件箱可见、短信应用来信通知、RP-ACK的SIP202、接收后继续常驻、多轮短周期及停用清理。声音/横幅取决于通知设置。

离线验证：41项短信断言、独立Digest向量、重复/折行身份与路由、多Contact、二进制帧、半包超时和大量保活。

未完成：发送短信（实验RP28）、长短信及其它UDH消息、替换/状态短信、第三方SMS_RECEIVED和验证码自动填充、长期耗电/切网、重启自启动实测、真实通话共存测试。原厂Voice能力为true不等于实际通话已验证。

## 发布范围

仅提交源码、模块脚本、可配置构建脚本和合成测试。保留GPL-2.0及原作者归属。设备日志、验证码、电话号码、IMSI/IMEI/ICCID、认证密钥、抓包、固件和设备提取库均不发布。
完整构建及暂停说明见[README.md](README.md)。

## 本次整理到 VoWiFiPortedRom fork

- 在 `ports/android11/` 独立收录 Magisk 模块、Java/JNI/C 源码、构建脚本和测试。
- phhusson 源码固定到明确基线，保留 GPL-2.0 原许可证及可审阅补丁，详见 THIRD_PARTY.md。
- 编译和测试脚本改为读取目录内的 vendored 源码，不再依赖另一个 ims 仓库的检出位置。
- 原仓库 Android 17 方案保持独立；当前新增内容仍是依赖原厂隧道的 Android 11 短信接收模块。
