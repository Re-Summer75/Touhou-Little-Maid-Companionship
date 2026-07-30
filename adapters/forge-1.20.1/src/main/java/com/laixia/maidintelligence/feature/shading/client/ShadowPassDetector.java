package com.laixia.maidintelligence.feature.shading.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * 可选探测 Oculus/Iris 的实体阴影阶段。仅缓存公开静态状态方法，不建立编译期依赖；
 * 未安装光影加载器或内部接口变化时保守退化为普通渲染。
 */
@OnlyIn(Dist.CLIENT)
public final class ShadowPassDetector {
    private static final String[] STATE_CLASSES = {
            "net.irisshaders.iris.shadows.ShadowRenderingState",
            "net.coderbot.iris.shadows.ShadowRenderingState"
    };
    private static final Probe PROBE = createProbe();
    private static volatile int testingOverride = -1;

    private ShadowPassDetector() {
    }

    public static boolean isActive() {
        int override = testingOverride;
        if (override >= 0) {
            return override != 0;
        }
        try {
            return PROBE.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void setTestingOverride(boolean active) {
        testingOverride = active ? 1 : 0;
    }

    static void clearTestingOverride() {
        testingOverride = -1;
    }

    private static Probe createProbe() {
        ClassLoader loader = ShadowPassDetector.class.getClassLoader();
        for (String className : STATE_CLASSES) {
            try {
                Class<?> stateClass = Class.forName(
                        className,
                        false,
                        loader
                );
                MethodHandle method = MethodHandles.publicLookup().findStatic(
                        stateClass,
                        "areShadowsCurrentlyBeingRendered",
                        MethodType.methodType(boolean.class)
                );
                return () -> (boolean) method.invokeExact();
            } catch (ClassNotFoundException
                     | NoSuchMethodException
                     | IllegalAccessException
                     | LinkageError
                     | SecurityException ignored) {
                // 尝试下一个兼容包名。
            }
        }
        return () -> false;
    }

    @FunctionalInterface
    private interface Probe {
        boolean isActive() throws Throwable;
    }
}
