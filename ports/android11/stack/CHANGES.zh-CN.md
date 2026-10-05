# Android 11 三组件替换的修改与验证

保留 `android11-companion-0.2.0-baseline`。旧模块仍采用原厂隧道与语音，
加独立短信接收程序；本目录是另一套可回退的 IWLAN／QNS／MMTEL 替换。

## 新增实现

- IWLAN APK：API30 DataService／NetworkService，SIM EAP-AKA、自建 ePDG
  IKE/child、IPsec 接口、内层 IPv4、DNS/P-CSCF、DataCallResponse 与网络变化通知。
  使用手机自带 Android11 IKE 库，不更新 APEX、基带或 vendor 二进制。
- QNS APK：为指定 VOXI SIM 上报 IWLAN IMS 接入；检查用户 WFC 开关、实体
  Wi-Fi 与本次开机的限时门控。根脚本续期，失去监督时自动撤回。
- IMS APK：从保留的 phhusson 源码快照生成增量，适配 Android11 生命周期、
  socket/IPsec 描述符、WLAN 注册、能力通知、网络重连及注册刷新。
- 系统短信：通过 ImsSmsImplBase 收发；系统负责分段合并、入库、短信应用通知。
  保留网络 RP 引用，不把 TP messageRef 当 RP 引用；按系统收妥结果回复 RP-ACK
  或 RP-ERROR，再确认运营商接受该回复。没有直接插入短信数据库。
- 语音：补齐 outgoing call profile／状态／回调；网络进展到达后才发 progressing，
  避免同步回调清空 dial 尚未返回的 mPendingMO。处理 precondition、独立媒体线程
  清理、后续呼叫状态重置、已接通呼叫 BYE；无 RNNoise JNI 时透传 PCM。
- Magisk：三个特权 APK 和权限白名单；事务备份运营商 override 文件，切换
  AP-assisted 并重载电话服务；五分钟试验、常驻监督、卸载回退，以及独立于模块
  挂载的 service.d 恢复入口。关机期间 Binder 消失不再撤销常驻事务。
- 诊断／构建：API 枚举、独立隧道探测、注册／能力检查、固定授权 INFO 短信测试，
  可恢复短码策略，ECJ/Kotlin/D8 构建与外部密钥签名。源码不包含私钥、短信正文、
  SIM 身份或 ROM 提取物。

## 已验证

设备 raphael、MIUI V12.5.1.0.RFKMIXM、Android11/API30、VOXI 23415、slot index 1。

1. 框架绑定新 IWLAN/QNS/IMS；独立隧道建立、IMS NetworkAgent 出现。
2. SIP REGISTER 200；Android 报告 WLAN 及 voice/SMS 能力。
3. 系统拨号到授权的 191：正常 Telecom connection，上下行媒体观测；系统挂断
   返回 true，网络 BYE 200，双卡 call state 回到 0。实际听感待用户确认。
4. INFO 发往授权的 85075：系统 ImsSmsDispatcher cap=true，SIP 202、网络 RP-ACK、
   sent result RESULT_OK。四段下行各得到系统接收成功与网络确认，合并入库并有
   Google Messages 通知。不是把代理程序直接写入数据库当作接收成功。
5. 定时回退、临时短码策略还原、真实禁用模块重启后恢复原厂配置并启动旧模块。
6. 修正关机误回退与开机绑定过渡后，再次真实重启：常驻配置保留、自动重载并
   注册；没有手动重载的情况下，85075 INFO 上行和四段回复下行再次完整成功。

## 限制与待测

最初开机绑定存在能力覆盖，导致短信 dispatcher cap=false，而后注册的应用
监听器却看到 true。电话进程重载后短信收发复测成功；开机自动执行一次空闲
重载的版本已通过真实重启后的端到端 SMS 测试，验收包含 dispatcher cap=true、
发送结果、RP-ACK 和每段下行的系统及网络确认，不只检查 SIP 200。
具体覆盖来源尚未精确归因。

只开放当前已测试设备/SIM 的安装与替换。尚不宣称 Android12–17、其他 SIM、
IPv6-only、来电、DTMF、通话保持/转移、长通话刷新、VoLTE 回退或紧急呼叫支持。
Magisk 安全模式可能连独立 service.d 恢复入口也禁用，需要保留人工恢复手段。
来源与 GPL-2.0 标注见父目录 THIRD_PARTY.md。
