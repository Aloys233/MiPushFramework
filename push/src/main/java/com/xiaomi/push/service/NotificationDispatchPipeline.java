package com.xiaomi.push.service;

import com.xiaomi.xmsf.push.utils.PackageConfig;

import java.util.Set;

/**
 * Keeps wake, notify, and open notification dispatch phases isolated
 * so a failure in one stage cannot drop subsequent stages.
 */
final class NotificationDispatchPipeline {
    static final String STAGE_WAKE = "wake";
    static final String STAGE_NOTIFY = "notify";
    static final String STAGE_OPEN = "open";

    private NotificationDispatchPipeline() {
    }

    static DispatchPlan planFromOperations(Set<String> operations) {
        if (operations == null) {
            return DispatchPlan.notifyOnly();
        }
        try {
            return new DispatchPlan(
                    operations.contains(PackageConfig.OPERATION_WAKE),
                    !operations.contains(PackageConfig.OPERATION_IGNORE),
                    operations.contains(PackageConfig.OPERATION_OPEN));
        } catch (RuntimeException ignored) {
            return DispatchPlan.notifyOnly();
        }
    }

    static void dispatch(
            DispatchPlan plan,
            Stage wake,
            Stage notify,
            Stage open,
            FailureHandler failureHandler) {
        DispatchPlan effectivePlan = plan == null ? DispatchPlan.notifyOnly() : plan;
        runSafely(effectivePlan.wake, STAGE_WAKE, wake, failureHandler);
        runSafely(effectivePlan.notify, STAGE_NOTIFY, notify, failureHandler);
        runSafely(effectivePlan.open, STAGE_OPEN, open, failureHandler);
    }

    private static void runSafely(
            boolean enabled,
            String stage,
            Stage action,
            FailureHandler failureHandler) {
        if (!enabled || action == null) {
            return;
        }
        try {
            action.run();
        } catch (Exception e) {
            try {
                if (failureHandler != null) {
                    failureHandler.onFailure(stage, e);
                }
            } catch (Exception ignored) {
            }
        }
    }

    interface Stage {
        void run() throws Exception;
    }

    interface FailureHandler {
        void onFailure(String stage, Exception exception);
    }

    static final class DispatchPlan {
        final boolean wake;
        final boolean notify;
        final boolean open;

        DispatchPlan(boolean wake, boolean notify, boolean open) {
            this.wake = wake;
            this.notify = notify;
            this.open = open;
        }

        static DispatchPlan notifyOnly() {
            return new DispatchPlan(false, true, false);
        }
    }
}
