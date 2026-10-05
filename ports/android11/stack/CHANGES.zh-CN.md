# Android 11 三组件替换的修改与验证

保留 `android11-companion-0.2.0-baseline`。旧模块仍采用原厂隧道与语音，
加独立短信接收程序；本目录是另一套可回退的 IWLAN／QNS／MMTEL 替换。

## 0.5.0 选卡身份协议（2026-10-06）

- 新事务原子保存 slot/subId，显式传给 CarrierConfig helper 和每槽租约。
  旧无 owner 事务仅按原本固定的 (1,1) 解释，不从界面当前选卡推断。
- UI／CLI 重载、常驻、回退核对记录的 owner；helper 再检查实际活动订阅。
  原卡不可见时回退保留事务和备份，不转而清除另一卡的配置。
- 实际错误 slot/sub 的重载、常驻、回退、check/read/clear/apply 均被拒绝，
  事务、备份、常驻标记和电话进程保持不变。UI 空 SIM1 可明确恢复 SIM2；
  再选 SIM2 建新显式事务，恢复 WLAN 语音/SMS 注册。
- 当前仍是全局单事务，不宣称双卡同时替换完成。后续要求见 TRANSACTIONS.md。
- 最终 0.5.0 在机模块与构建逐项哈希一致；真实重启保留 EXPLICIT 归属并自动
  注册 WLAN 语音/SMS。INFO→85075 返回 RESULT_OK，四段下行系统接受且四个
  RP-ACK 获 SIP202，合并为一个收件箱记录并生成通知；短码策略恢复原值 0。

## 0.4.0 事务与兼容性修正（2026-10-06）

- 控制器支持 IWLAN=1、QNS=2、IMS=4 的组合掩码；未选择的 provider 保留
  原有效值，仅选 IWLAN 时才改全局 operation mode。部分组合限时试用，禁止常驻。
- CarrierConfig 使用平台 Binder 接口反射，不硬编码 transaction 编号。回退先清
  内存与持久覆盖并确认异步删除，再还原文件，重载后核对原有效配置。
- 防止把遗留替换 provider 当作原配置；目录不可读时在建事务前拒绝修改。
  已保留异常备份并恢复较早的原始备份，实际确认 provider 清除与 legacy 还原。
- 修改操作使用 flock 串行化；每个监督／超时进程携带事务 token，旧超时事件不能
  删除新试验。实际调用旧 token 的 expire 返回 STALE，新事务保持完整。
- IMS 按 slot/subscription 分离 feature、registration、config、alarm。功能发布
  检查授权订阅，初始化另等 SIM READY；旧回调持有原 registration 对象。
- IWLAN 在建链与发布 child 前重查订阅；兼容新旧 IKE proposal/异常回调名字，
  仅接口不存在时回退。创建与关闭 session 共用资源锁，防止并发遗漏清理。
- 新增固定版本的 Android11–17 框架样本检查、反射回退与租约契约测试；服务
  235 项引用、包含工具 301 项引用均无缺失。静态通过不等于新 ROM 实机通过。
- 工具自身 root 界面已实际测试 IMS-only、原配置回退、完整组合和常驻；IMS-only
  未恢复 WLAN，完整组合恢复语音/SMS 注册。控制器仍只开放当前已验证卡槽。
- 最终 0.4.0 模块和三 APK 的在机 SHA256 与构建一致；实际重启后自动建链。
  INFO→85075 发送 RESULT_OK，四段回复经系统接收、四个 RP-ACK 获 SIP202，
  合并为一个收件箱记录并生成 Google Messages 通知，短码策略恢复原值 0。

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
