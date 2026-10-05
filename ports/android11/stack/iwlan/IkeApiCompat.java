// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.iwlan;

import java.lang.reflect.Method;

/** Select by actual method availability, including API30 backports. */
final class IkeApiCompat {
    private IkeApiCompat(){}
    static void addIkeProposal(Object builder,Object proposal,Class<?> proposalClass)throws ReflectiveOperationException{
        Method method;
        try{method=builder.getClass().getMethod("addIkeSaProposal",proposalClass);}
        catch(NoSuchMethodException oldApi){method=builder.getClass().getMethod("addSaProposal",proposalClass);}
        // An invocation failure is real. Do not mask it by trying another alias.
        method.invoke(builder,proposal);
    }
    static Object newIkeBuilder(Class<?> builderClass,Class<?> contextClass,Object context)throws ReflectiveOperationException{
        try{return builderClass.getConstructor(contextClass).newInstance(context);}
        catch(NoSuchMethodException publicApi){return builderClass.getConstructor().newInstance();}
    }
}
