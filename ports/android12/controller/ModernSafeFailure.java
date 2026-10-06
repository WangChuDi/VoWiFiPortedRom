// SPDX-License-Identifier: GPL-2.0
import java.util.*;

/** Export only fixed reason tokens; framework/filesystem messages can contain identity. */
final class ModernSafeFailure {
    private static final Set<String> REASONS=new HashSet<>(Arrays.asList(
        "prepared-installation-required","selected-permission-owner-changed",
        "external-slot-lease-change-refused","selection-record-refused",
        "owner-recovery-must-finish-first","carrier-recovery-must-finish-first",
        "selected-data-appop-restore-unconfirmed","external-selected-data-appop-change",
        "selected-permission-restore-not-settled","role-broker-restore-failed",
        "fixture-outer-restoration-unconfirmed","fixture-role-repair-unconfirmed",
        "supervisor-owner-record-refused","supervisor-untracked-owner-state",
        "selection-archive-conflict","restorable-transaction-required",
        "selected-subscription-changed","modern-controller-busy",
        "fixture-supervised-trial-unconfirmed","fixture-installation-history-unconfirmed",
        "fixture-file-refused","fresh-ready-installation-required","fresh-ready-fixture-required",
        "original-slot-lease-changed-before-archive","enabled-selection-module-required",
        "selected-providers-unconfirmed","selected-providers-changed",
        "selection-archive-carrier-unavailable","selection-archive-phase-refused",
        "original-carrier-required-before-archive","untracked-carrier-state-recovery-required"));
    static String reason(Throwable error){String value=error.getMessage();return REASONS.contains(value)?value:"unclassified";}
    static String origin(Throwable error) {
        for(StackTraceElement frame:error.getStackTrace())if(frame.getClassName().matches("Modern[A-Za-z0-9]+")&&frame.getMethodName().matches("[A-Za-z0-9_<>]+"))return frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber();
        return "external";
    }
}
