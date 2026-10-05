// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.iwlan;

import dev.codex.vowifi.common.ProfileGate;
import java.lang.reflect.InvocationTargetException;

/** No Android execution, network access, SIM authentication or device mutation. */
public final class CompatibilityContractTest {
    public static final class Proposal{}
    public static final class Context{}
    public static final class LegacyBuilder{
        int calls;
        public void addSaProposal(Proposal value){calls++;}
    }
    public static final class ModernBuilder{
        int modern,legacy;
        public void addIkeSaProposal(Proposal value){modern++;}
        public void addSaProposal(Proposal value){legacy++;}
    }
    public static final class RejectingBuilder{
        int legacy;
        public void addIkeSaProposal(Proposal value){throw new SecurityException("denied");}
        public void addSaProposal(Proposal value){legacy++;}
    }
    public static final class LegacyConstructor{
        final Context context;
        public LegacyConstructor(Context value){context=value;}
    }
    public static final class PublicConstructor{public PublicConstructor(){}}
    public static final class RejectingConstructor{
        public RejectingConstructor(Context value){throw new SecurityException("denied");}
        public RejectingConstructor(){throw new AssertionError("must-not-fall-back");}
    }
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
    public static void main(String[] args)throws Exception{
        Proposal proposal=new Proposal();
        LegacyBuilder old=new LegacyBuilder();
        IkeApiCompat.addIkeProposal(old,proposal,Proposal.class);
        check(old.calls==1,"legacy proposal path");
        ModernBuilder modern=new ModernBuilder();
        IkeApiCompat.addIkeProposal(modern,proposal,Proposal.class);
        check(modern.modern==1&&modern.legacy==0,"prefer modern proposal name");
        RejectingBuilder rejecting=new RejectingBuilder();
        try{IkeApiCompat.addIkeProposal(rejecting,proposal,Proposal.class);throw new AssertionError("invocation rejection lost");}
        catch(InvocationTargetException error){check(error.getCause() instanceof SecurityException&&rejecting.legacy==0,"no fallback after invocation denial");}
        Context context=new Context();
        LegacyConstructor legacy=(LegacyConstructor)IkeApiCompat.newIkeBuilder(LegacyConstructor.class,Context.class,context);
        check(legacy.context==context,"preserve legacy context");
        check(IkeApiCompat.newIkeBuilder(PublicConstructor.class,Context.class,context) instanceof PublicConstructor,"public no-arg constructor");
        try{IkeApiCompat.newIkeBuilder(RejectingConstructor.class,Context.class,context);throw new AssertionError("constructor rejection lost");}
        catch(InvocationTargetException error){check(error.getCause() instanceof SecurityException,"no constructor fallback after denial");}
        check(ProfileGate.permits(1,1,-1,100,90100,4,4),"legacy authorization retained");
        check(!ProfileGate.permits(0,1,-1,100,90100,4,4),"legacy does not authorize other slot");
        check(!ProfileGate.permits(1,7,-1,100,90100,4,4),"legacy does not authorize new subscription");
        check(ProfileGate.permits(0,41,41,100,90100,4,4)&&ProfileGate.permits(1,7,7,100,90100,4,4),"independent explicit subscriptions");
        check(!ProfileGate.permits(0,42,41,100,90100,4,4),"SIM swap rejects prior subscription");
        check(!ProfileGate.permits(0,41,41,100,100,4,4)&&ProfileGate.permits(1,7,7,100,90100,4,4),"one expiry does not authorize or expire another");
        check(!ProfileGate.permits(1,7,7,100,90100,5,4),"prior boot rejected");
        check(!ProfileGate.permits(1,7,7,100,90100,-1,-1),"unknown boot rejected");
        check(!ProfileGate.permits(1,7,7,100,120101,4,4),"overlong deadline rejected");
        System.out.println("CompatibilityContractTest: PASS (reflection failure semantics and per-subscription gate isolation)");
    }
}
