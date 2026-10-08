// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

/** Real-session observations remain separate from independent UDP results. */
final class NetworkSessionEvidence {
    static String describe(boolean complete,boolean stable,int transport,boolean tunnelObserved,
            boolean ikeOpen,boolean childOpen,boolean imsObserved,boolean registered,int sipStatus){
        if(!complete||!stable)return "本次检查未完成或进程稳定性未确认；不使用它判定实际会话，请刷新。独立 UDP 无响应仍为未确定。";
        StringBuilder s=new StringBuilder();
        s.append(transport==2?"本次框架回调确认 IMS 在 WLAN 注册。":"本次尚未确认 IMS 在 WLAN 注册；不能据此直接认定 UDP 被封。");
        if(tunnelObserved)s.append("\n所选 SIM 的 IKE：").append(ikeOpen?"已打开":"未打开").append("；Child SA：").append(childOpen?"已打开":"未打开").append('。');
        else s.append("\n未取得可归属的替换隧道状态；原厂隧道可能不提供此观测。");
        if(imsObserved&&registered&&sipStatus==200)s.append("\n本代 IMS 曾收到注册成功的 SIP 200；这是实际协议交互的证据。");
        s.append("\n这些会话状态比独立探测超时更有参考价值，但不是此刻每个 UDP 端口的连通测试，也不保证下一次通话或短信成功。");
        s.append("\n要确认此刻的数据流，需要观察真实会话的新响应或成功收发；只看到发包、历史注册或接口存在还不够。");
        return s.toString();
    }
}
