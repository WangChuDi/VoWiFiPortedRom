// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.iwlan;

import android.net.*;
import android.os.Handler;
import android.telephony.DataFailCause;
import android.telephony.data.*;
import dev.codex.vowifi.common.StackProfile;
import java.net.*;
import java.util.*;

/** Exercises the production provider and address recipe; Android boundaries are fakes. */
public final class ModernIwlanContractTest {
    private static int assertions;
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static InetAddress ip(String s)throws Exception{return InetAddress.getByName(s);}
    private static LinkAddress la(String s)throws Exception{return new LinkAddress(ip(s),s.contains(":")?64:32);}
    private static LinkProperties source(String...ips)throws Exception{
        LinkProperties p=new LinkProperties();for(String s:ips)p.addLinkAddress(la(s));return p;
    }
    private static DataProfile profile(int normal,int roaming){return new DataProfile("ims",normal,roaming);}
    private static DataService.DataServiceProvider provider(int slot){return new ModernIwlanDataService().onCreateDataServiceProvider(slot);}
    private static DataServiceCallback setup(DataService.DataServiceProvider p,int protocol,int reason,LinkProperties source){
        DataServiceCallback cb=new DataServiceCallback();p.setupDataCall(5,profile(protocol,protocol),false,true,reason,source,0,null,null,true,cb);Handler.drain();return cb;
    }
    private static EpdgSession last(){return EpdgSession.created.get(EpdgSession.created.size()-1);}
    public static class RecipeBuilder {
        final List<String> calls=new ArrayList<>();
        public RecipeBuilder addPcscfServerRequest(int family){calls.add("pcscf:"+family);return this;}
        public RecipeBuilder addInternalAddressRequest(int family){calls.add("address-family:"+family);return this;}
        public RecipeBuilder addInternalAddressRequest(Inet4Address address){calls.add("source4:"+address.getHostAddress());return this;}
        public RecipeBuilder addInternalAddressRequest(Inet6Address address,int prefix){calls.add("source6:"+prefix);return this;}
        public RecipeBuilder addInternalDnsServerRequest(int family){calls.add("dns:"+family);return this;}
    }
    private static void recipes()throws Exception{
        RecipeBuilder legacy=new RecipeBuilder();EpdgAddressRequest.LEGACY.configurePcscf(legacy,2,10);EpdgAddressRequest.LEGACY.configureChild(legacy,2,10);
        check(legacy.calls.equals(Arrays.asList("pcscf:2","address-family:2","dns:2")),"legacy IPv4 recipe unchanged");
        EpdgAddressRequest dual=new EpdgAddressRequest(2,null,null,0);RecipeBuilder both=new RecipeBuilder();dual.configurePcscf(both,2,10);dual.configureChild(both,2,10);
        check(both.calls.equals(Arrays.asList("pcscf:2","pcscf:10","address-family:2","dns:2","address-family:10","dns:10")),"dual address/dns/pcscf requests");
        EpdgAddressRequest handover=new EpdgAddressRequest(2,(Inet4Address)ip("100.64.1.2"),(Inet6Address)ip("2001:db8::1"),64);
        RecipeBuilder kept=new RecipeBuilder();handover.configureChild(kept,2,10);
        check(kept.calls.equals(Arrays.asList("source4:100.64.1.2","dns:2","source6:64","dns:10")),"handover exact address overloads");
        handover.verifyAssigned(Arrays.asList(ip("100.64.1.2"),ip("2001:db8::1")));
        check(handover.preserveIpv6(ip("2001:db8::99"),64).equals(ip("2001:db8::1")),"same delegated prefix preserves source IID");
        check(handover.preserveIpv6(ip("2001:db8:1::99"),64).equals(ip("2001:db8:1::99")),"different delegated prefix never fabricated");
        check(handover.preserveIpv6(ip("2001:db8::99"),80).equals(ip("2001:db8::99")),"different delegated prefix length never fabricated");
        EpdgAddressRequest uneven=new EpdgAddressRequest(1,null,(Inet6Address)ip("2001:db8::1234"),73);
        check(uneven.preserveIpv6(ip("2001:db8::abcd"),73).equals(uneven.ipv6),"partial-byte prefix masks IID correctly");
        check(uneven.preserveIpv6(ip("2001:db8:0:0:8000::1"),73).equals(ip("2001:db8:0:0:8000::1")),"partial-byte prefix rejects changed network bit");
        try{handover.verifyAssigned(Arrays.asList(ip("100.64.1.3"),ip("2001:db8::1")));throw new AssertionError("accepted changed source");}catch(IllegalStateException expected){assertions++;}
        EpdgAddressRequest onlySource4=new EpdgAddressRequest(2,(Inet4Address)ip("100.64.1.2"),null,0);
        check(onlySource4.wants4()&&!onlySource4.wants6(),"handover requests source families only");
        try{new EpdgAddressRequest(1,(Inet4Address)ip("100.64.1.2"),null,0);throw new AssertionError("family conflict");}catch(IllegalArgumentException expected){assertions++;}
        try{new EpdgAddressRequest(1,null,(Inet6Address)ip("2001:db8::1"),129);throw new AssertionError("prefix overflow");}catch(IllegalArgumentException expected){assertions++;}
    }
    private static void rejection()throws Exception{
        DataService.DataServiceProvider p=provider(0);int before=EpdgSession.created.size();
        DataServiceCallback cb=new DataServiceCallback();p.setupDataCall(5,profile(0,0),false,true,1,null,0,null,null,false,cb);Handler.drain();
        check(cb.count==1&&cb.result==0&&cb.response.cause==DataFailCause.MATCH_ALL_RULE_NOT_ALLOWED,"URSP rule constraint survives dispatch");
        cb=new DataServiceCallback();p.setupDataCall(5,profile(0,0),false,true,1,null,0,new NetworkSliceInfo(),null,true,cb);Handler.drain();
        check(cb.response.cause==DataFailCause.SLICE_REJECTED,"slice is explicitly rejected");
        cb=new DataServiceCallback();p.setupDataCall(5,profile(0,0),false,true,1,null,1,null,null,true,cb);Handler.drain();
        check(cb.response.cause==DataFailCause.SERVICE_OPTION_NOT_SUPPORTED,"nonzero PDU cannot fake EPC success");
        cb=new DataServiceCallback();p.setupDataCall(5,profile(0,0),false,true,1,null,0,null,new TrafficDescriptor(),true,cb);Handler.drain();
        check(cb.response.cause==DataFailCause.SERVICE_OPTION_NOT_SUPPORTED,"traffic descriptor cannot fake matching");
        cb=new DataServiceCallback();p.setupDataCall(5,profile(0,0),false,true,1,null,16,null,null,true,cb);Handler.drain();
        check(cb.result==DataServiceCallback.RESULT_ERROR_INVALID_ARG,"PDU range checked");
        cb=new DataServiceCallback();p.setupDataCall(5,profile(0,1),true,false,1,null,0,null,null,true,cb);Handler.drain();
        check(cb.response.cause==DataFailCause.DATA_ROAMING_SETTINGS_DISABLED,"roaming request policy honored");
        cb=new DataServiceCallback();p.setupDataCall(3,profile(0,0),false,true,1,null,0,null,null,true,cb);Handler.drain();
        check(cb.result==DataServiceCallback.RESULT_ERROR_UNSUPPORTED,"wrong access network refused");
        cb=setup(p,0,3,null);check(cb.result==DataServiceCallback.RESULT_ERROR_INVALID_ARG,"missing handover source refused");
        cb=setup(p,0,3,source("100.64.1.2","100.64.1.3"));check(cb.result==DataServiceCallback.RESULT_ERROR_INVALID_ARG,"multiple source addresses not silently dropped");
        check(EpdgSession.created.size()==before,"rejections created no tunnel");p.close();Handler.drain();
    }
    private static void normalAndHandover()throws Exception{
        DataService.DataServiceProvider p=provider(0);DataServiceCallback cb=setup(p,2,1,null);EpdgSession session=last();
        check(cb.count==0&&session.slot==0&&session.sub==11&&session.request.protocol==2,"normal request captures owner and protocol");
        session.open(Arrays.asList(la("100.64.1.2"),la("2001:db8::1")));Handler.drain();
        check(cb.count==1&&cb.result==0&&cb.response.cause==0&&cb.response.protocol==2,"dual response success");
        check(cb.response.mtu4==1280&&cb.response.mtu6==1280&&cb.response.gateways.size()==2&&cb.response.pdu==0,"response reflects actual families and EPC identity");
        int count=EpdgSession.created.size();DataServiceCallback duplicate=setup(p,2,1,null);
        check(duplicate.response==cb.response&&EpdgSession.created.size()==count,"same active request reuses tunnel");
        DataServiceCallback conflict=setup(p,0,1,null);check(conflict.result==DataServiceCallback.RESULT_ERROR_BUSY&&session.closes==0,"protocol conflict preserves active tunnel");
        DataServiceCallback wrong=new DataServiceCallback();p.deactivateDataCall(9999,1,wrong);Handler.drain();check(wrong.result==2&&session.closes==0,"wrong CID cannot deactivate");
        p.close();Handler.drain();check(session.closes==1,"provider owns only its session");

        p=provider(0);LinkProperties original=source("100.64.2.2");cb=new DataServiceCallback();
        p.setupDataCall(5,profile(0,0),false,true,3,original,0,null,null,true,cb);
        original.clear();original.addLinkAddress(la("100.64.9.9"));Handler.drain();session=last();
        check(session.request.ipv4.equals(ip("100.64.2.2")),"handover source snapshotted before queue");
        session.open(Collections.singletonList(la("100.64.2.3")));Handler.drain();
        check(cb.count==1&&cb.response.cause!=0&&cb.response.mode==DataCallResponse.HANDOVER_FAILURE_MODE_DO_FALLBACK&&session.closes==1,"changed IP fails handover with source fallback");
        cb=setup(p,0,3,source("100.64.2.2"));session=last();session.open(Collections.singletonList(la("100.64.2.2")));Handler.drain();
        check(cb.count==1&&cb.response.cause==0,"exact source handover accepted");
        conflict=setup(p,0,3,source("100.64.2.9"));check(conflict.response.cause!=0&&session.closes==0,"different source request cannot destroy current tunnel");
        p.close();Handler.drain();
        p=provider(0);cb=new DataServiceCallback();p.setupDataCall(5,profile(0,1),true,true,1,null,0,null,null,true,cb);Handler.drain();
        check(last().request.protocol==1,"roaming protocol selected when allowed");p.close();Handler.drain();
    }
    private static void ownership()throws Exception{
        DataService.DataServiceProvider failedProvider=provider(0);DataServiceCallback failedCb=setup(failedProvider,0,1,null);EpdgSession failedSession=last();
        failedSession.listener.closed("negotiation-failed");Handler.drain();
        check(failedCb.count==1&&failedCb.result==0&&failedCb.response.cause!=0&&failedSession.closes==1,"closed callback completes failed request exactly once");
        failedSession.listener.closed("late-close");Handler.drain();check(failedCb.count==1,"repeated close cannot complete twice");
        failedProvider.close();Handler.drain();
        DataService.DataServiceProvider p=provider(0);DataServiceCallback cb=setup(p,0,1,null);EpdgSession old=last();
        old.open(Collections.singletonList(la("100.64.1.2")));p.close();Handler.drain();
        check(cb.count==1&&cb.result==4&&p.lastList.isEmpty(),"retirement rejects queued success immediately");
        old.listener.closed("late");old.open(Collections.singletonList(la("100.64.1.2")));Handler.drain();
        check(cb.count==1&&p.lastList.isEmpty(),"retired callbacks cannot republish");
        p=provider(0);cb=setup(p,0,1,null);old=last();StackProfile.subs[0]=33;
        DataServiceCallback replacement=setup(p,0,1,null);EpdgSession next=last();
        check(cb.result==4&&old.closes==1&&next.sub==33,"subscription change retires old session");
        old.open(Collections.singletonList(la("100.64.1.2")));Handler.drain();check(replacement.count==0,"old subscription callback cannot mutate replacement");
        next.open(Collections.singletonList(la("100.64.3.2")));Handler.drain();check(replacement.count==1&&replacement.response.cause==0,"replacement owner publishes");
        DataService.DataServiceProvider other=provider(1);DataServiceCallback otherCb=setup(other,0,1,null);EpdgSession otherSession=last();
        p.close();Handler.drain();otherSession.open(Collections.singletonList(la("100.64.4.2")));Handler.drain();
        check(otherCb.response.cause==0&&otherSession.slot==1&&otherSession.sub==22&&otherSession.closes==0,"closing one provider preserves another slot");
        other.close();Handler.drain();StackProfile.subs[0]=11;
        p=provider(0);p.setupDataCall(5,profile(0,0),false,true,1,null,0,null,null,true,null);Handler.drain();
        cb=setup(p,0,1,null);check(cb.result==3,"null callback still reserves pending session");p.close();Handler.drain();
        p=provider(0);cb=setup(p,0,1,null);StackProfile.subs[0]=-1;last().open(Collections.singletonList(la("100.64.1.2")));Handler.drain();
        check(cb.result==4&&p.lastList.isEmpty(),"SIM removal before open rejects publication");p.close();Handler.drain();StackProfile.subs[0]=11;
    }
    private static void sourceHandover()throws Exception{
        DataService.DataServiceProvider p=provider(0);DataServiceCallback setup=setup(p,0,1,null);EpdgSession session=last();
        session.open(Collections.singletonList(la("100.64.1.2")));Handler.drain();int cid=setup.response.id;
        DataServiceCallback cb=new DataServiceCallback();p.startHandover(cid+99,cb);Handler.drain();
        check(cb.count==1&&cb.result==2&&session.closes==0,"wrong source CID cannot begin handover");
        cb=new DataServiceCallback();p.startHandover(cid,cb);Handler.drain();check(cb.result==0&&session.closes==0,"handover starts without releasing source");
        cb=new DataServiceCallback();p.startHandover(cid,cb);Handler.drain();check(cb.result==3,"duplicate source handover is busy");
        cb=new DataServiceCallback();p.deactivateDataCall(cid,1,cb);Handler.drain();check(cb.result==3&&session.closes==0,"normal deactivation preserves source during handover");
        cb=new DataServiceCallback();p.cancelHandover(cid,cb);Handler.drain();check(cb.result==0&&session.closes==0,"cancel retains source tunnel");
        DataServiceCallback list=new DataServiceCallback();p.requestDataCallList(list);Handler.drain();check(list.list.size()==1&&list.list.get(0).id==cid,"cancel retains active call response");
        cb=new DataServiceCallback();p.startHandover(cid,cb);Handler.drain();
        cb=new DataServiceCallback();p.deactivateDataCall(cid,3,cb);Handler.drain();check(cb.result==0&&session.closes==1,"successful handover releases source when framework confirms");
        cb=new DataServiceCallback();p.deactivateDataCall(cid,3,cb);Handler.drain();check(cb.result==2&&session.closes==1,"ended CID cannot fake another successful release before replacement setup");
        setup=setup(p,0,1,null);EpdgSession next=last();next.open(Collections.singletonList(la("100.64.2.2")));Handler.drain();
        check(setup.response.id!=cid,"replacement call has a distinct generation CID");
        cb=new DataServiceCallback();p.deactivateDataCall(cid,3,cb);Handler.drain();check(cb.result==2&&next.closes==0,"late source release cannot close replacement call");
        StackProfile.subs[0]=44;list=new DataServiceCallback();p.requestDataCallList(list);Handler.drain();
        check(list.list.isEmpty()&&next.closes==1,"list observation withdraws stale SIM owner");
        cb=new DataServiceCallback();p.startHandover(setup.response.id,cb);Handler.drain();check(cb.result==4,"stale SIM cannot advertise source handover readiness");
        p.close();Handler.drain();StackProfile.subs[0]=11;
    }
    private static void closeDuringPublication()throws Exception{
        final DataService.DataServiceProvider p=provider(0);
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1),attempted=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean returned=new java.util.concurrent.atomic.AtomicBoolean();
        DataServiceCallback cb=new DataServiceCallback(){@Override public void onSetupDataCallComplete(int r,DataCallResponse d){
            entered.countDown();try{if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("publication release timeout");}catch(InterruptedException e){throw new RuntimeException(e);}
            super.onSetupDataCallComplete(r,d);
        }};
        p.setupDataCall(5,profile(0,0),false,true,1,null,0,null,null,true,cb);Handler.drain();EpdgSession session=last();
        session.open(Collections.singletonList(la("100.64.1.2")));
        Thread publication=new Thread(Handler::drain);publication.start();
        check(entered.await(2,java.util.concurrent.TimeUnit.SECONDS),"publication entered actual provider lifetime boundary");
        Thread retirement=new Thread(()->{attempted.countDown();p.close();returned.set(true);});retirement.start();
        check(attempted.await(2,java.util.concurrent.TimeUnit.SECONDS),"retirement attempted during publication");
        Thread.sleep(40);check(!returned.get(),"retirement cannot return while a success is being published");
        release.countDown();publication.join(2000);retirement.join(2000);Handler.drain();
        check(returned.get()&&cb.count==1&&p.lastList.isEmpty()&&session.closes==1,"retirement linearizes after publication and withdraws source");
    }
    public static void main(String[]args)throws Exception{
        recipes();rejection();normalAndHandover();ownership();sourceHandover();closeDuringPublication();
        System.out.println("Modern IWLAN production-contract PASS assertions="+assertions+" (controlled boundaries; not a device test)");
    }
}
