// SPDX-License-Identifier: GPL-2.0
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Parses only exact per-phone manager sections; logs are never state evidence. */
final class ModernIwlanObservation {
    static final class Result {
        final Boolean legacy, cachedWlan;
        final boolean transportManager, accessNetworksManager;
        Result(Boolean legacy, Boolean cachedWlan, boolean transport, boolean access) {
            this.legacy=legacy;this.cachedWlan=cachedWlan;
            transportManager=transport;accessNetworksManager=access;
        }
    }
    private static final class Section {
        final String kind;final int slot,indent;
        Boolean legacy,cachedWlan;
        Section(String kind,int slot,int indent){this.kind=kind;this.slot=slot;this.indent=indent;}
    }
    static Result read(String dump,int slot)throws IOException {
        if(dump==null||dump.length()>2097152||slot<0||slot>7)throw new IOException("shared-mode-observation-refused");
        Pattern header=Pattern.compile("(AccessNetworksManager|TransportManager)-([0-9]+):?");
        Deque<Section> stack=new ArrayDeque<>();Map<String,Section> selected=new HashMap<>();
        for(String line:dump.split("\\r?\\n",-1)) {
            String text=line.trim();if(text.isEmpty())continue;
            int spaces=0;while(spaces<line.length()&&Character.isWhitespace(line.charAt(spaces)))spaces++;
            while(!stack.isEmpty()&&spaces<=stack.peek().indent)stack.pop();
            Matcher match=header.matcher(text);
            if(match.matches()) {
                int managerSlot;
                try{managerSlot=Integer.parseInt(match.group(2));}catch(NumberFormatException bad){throw new IOException("shared-mode-slot-format-refused");}
                Section section=new Section(match.group(1),managerSlot,spaces);stack.push(section);
                if(managerSlot==slot&&selected.put(section.kind,section)!=null)throw new IOException("shared-mode-manager-ambiguous");
                continue;
            }
            if(stack.isEmpty()||stack.peek().slot!=slot)continue;
            Section section=stack.peek();
            // Deeper sections (in particular Local logs) cannot supply direct state fields.
            if(spaces!=section.indent+2)continue;
            if(text.startsWith("isInLegacy=")) {
                if(section.legacy!=null||!text.matches("isInLegacy=(true|false)"))throw new IOException("shared-mode-legacy-format-refused");
                section.legacy=Boolean.valueOf(text.substring(11));
            }
            if(text.startsWith("mAvailableTransports=")) {
                if(section.cachedWlan!=null||!text.matches("mAvailableTransports=\\[(WWAN|WLAN)(,(WWAN|WLAN))*\\]"))throw new IOException("shared-mode-transports-format-refused");
                String[] names=text.substring(22,text.length()-1).split(",");
                Set<String> unique=new HashSet<>(Arrays.asList(names));
                if(unique.size()!=names.length||!unique.contains("WWAN"))throw new IOException("shared-mode-transports-format-refused");
                section.cachedWlan=unique.contains("WLAN");
            }
        }
        if(selected.isEmpty())throw new IOException("shared-mode-manager-unobserved");
        Section transport=selected.get("TransportManager"),access=selected.get("AccessNetworksManager");
        if(transport!=null&&access!=null&&transport.legacy!=null&&access.legacy!=null&&!transport.legacy.equals(access.legacy))throw new IOException("shared-mode-manager-disagreement");
        Section owner=transport!=null?transport:access;
        return new Result(owner.legacy,owner.cachedWlan,transport!=null,access!=null);
    }
}
