# 诊断应用与常驻修复（2026-10-06）

## 0.1.1 诊断修正

* 读取实际 SIM 状态；仅有活动订阅时不再默认显示“已就绪”。
* root 隐藏初始化接口、控制器和独立观测失败保留其他可用检查结果。
  空 SIM 仍显示实体 Wi-Fi 及控制器状态。
* 支持反射读取新版框架的网络订阅集合；权限过滤或缺少归属信息时显示
  无法归属的网络数量，不把其他卡的 IMS 网络归给当前卡。
* 明确 IKE 日志是当前进程的历史事件，不能当作实时隧道状态。
* 同签名更新安装及应用自身 root 检查通过：SIM2 状态 READY，IPsec 接口
  存在、两个 P-CSCF、WLAN 注册及语音/SMS 能力；空 SIM1 未误归属 SIM2。
* Android12–17 仍缺实机验证，未开放对应替换引擎或单组件组合。

## 新增应用

* 包名 `dev.codex.vowifi.tool`，可选择 SIM1 / SIM2，只读检查相应订阅。
* 展示 Wi-Fi、ePDG DNS、互联网 APN、WFC/漫游设置、运营商配置、
  provisioning、IMS 接口实际存在性、P-CSCF、注册制式及语音/SMS 能力。
* 能识别“框架仍报告 IMS 接口，但内核接口或 IWLAN 进程已消失”。
* IKE 信息仅限本模块本轮进程的状态；不把 DNS 解析当作 UDP 或 IKE 成功。
* 只在已验证的 API30 / raphael / VOXI / SIM2 / subId1 配置开放三组件
  整体试用、常驻、空闲重载和回退。Android12–17 及单组件组合尚未适配。
* 嵌入已构建模块，使用外部密钥构建/签名，不包含私钥或设备私有日志。

## 核心修复

* 实机确认 MIUI `AutoLockOffClean` 杀掉 IWLAN，内核隧道消失而框架残留
  LinkProperties，随后 SIP 重连出现 `BindException`。
* IWLAN 增加可见常驻通知；监督脚本发现组件进程消失时，仅在通话空闲且
  原测试 SIM 仍匹配时重载电话客户端，两次恢复之间至少间隔五分钟。
* TCP 构造时 bind 失败立即关闭新建 socket，避免重试累积资源。
* root 操作前重新核验设备、SIM 状态、运营商、slot/subId；apply/gate
  失败即时回退。备份及原短信模块继续保留。
* Windows Java 构建显式使用 UTF-8，避免中文常驻通知被本机编码破坏。

## 实测与边界

* 重启后仍选择新 IWLAN / QNS / IMS，恢复 WLAN 注册与语音/SMS 能力。
* 人工终止自己的 IWLAN 进程后，监督脚本自动恢复隧道和 IMS 注册。
* 应用安装、独立 root 授权、两个卡槽选择、空卡槽结果及重新拉起按钮通过
  实际界面验证；只有一张活动 SIM，未验证双卡同时注册。
* 最后一次授权 INFO→85075 测试返回发送成功，四段回复经框架接收、
  RP-ACK 获 SIP202，并观察到 Google Messages 通知记录。临时短码策略恢复。
* 长时间锁屏稳定性、其他 Android 版本、呼入/DTMF/切网等仍未完成验证。
  注册和能力为 true 不能替代这些测试。

保留 phhusson/ims GPL-2.0 来源、原许可证及 Suiying6023/VoWiFiPortedRom
架构参考标注，详情见 `../android11/THIRD_PARTY.md`。
