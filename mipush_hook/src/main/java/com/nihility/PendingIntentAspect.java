package com.nihility;

import android.app.PendingIntent;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.Arrays;

/**
 * 小米推送 SDK 是按 targetSdk < 31 编写的,创建 PendingIntent 时没有指定可变性标志。
 * 在 targetSdk >= 31 的 Android 12+ 上会抛:
 *   IllegalArgumentException: ... requires that one of FLAG_IMMUTABLE or FLAG_MUTABLE be specified
 * 该切面在被织入的 SDK 调用点上补上 FLAG_IMMUTABLE(仅当原本既没有 MUTABLE 也没有 IMMUTABLE 时)。
 * <p>
 * PendingIntent 的 getXxx 系列方法都把 flags 作为最后一个 int 参数(Bundle 重载为倒数第二个),
 * 因此从参数末尾向前找到第一个 Integer 即为 flags。
 */
@Aspect
public class PendingIntentAspect {
    private static final int FLAG_IMMUTABLE = PendingIntent.FLAG_IMMUTABLE;
    private static final int FLAG_MUTABLE = 0x02000000; // PendingIntent.FLAG_MUTABLE,API 31

    @Around("call(* android.app.PendingIntent.get*(..))")
    public Object ensureMutabilityFlag(final ProceedingJoinPoint joinPoint) throws Throwable {
        final Object[] args = joinPoint.getArgs();
        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i] instanceof Integer) {
                final int flags = (Integer) args[i];
                if ((flags & (FLAG_IMMUTABLE | FLAG_MUTABLE)) == 0) {
                    // 注意:不要用 args.clone()。ajc 内联 advice 时会把数组 clone() 错误地编译成
                    // ajc$superDispatch$...$clone(),导致 ART 抛 VerifyError。
                    final Object[] newArgs = Arrays.copyOf(args, args.length);
                    newArgs[i] = flags | FLAG_IMMUTABLE;
                    return joinPoint.proceed(newArgs);
                }
                break;
            }
        }
        return joinPoint.proceed();
    }
}
