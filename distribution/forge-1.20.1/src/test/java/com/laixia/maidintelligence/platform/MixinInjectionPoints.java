package com.laixia.maidintelligence.platform;

import com.google.gson.JsonObject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The obfuscation rule, applied to where an injection lands.
 *
 * <p>Split out of {@link MixinProductionNamesVerification} only because the
 * repository caps a source file at five hundred lines; the two halves state
 * one rule about two halves of the same annotation.
 */
final class MixinInjectionPoints {
    private MixinInjectionPoints() {
    }

    /**
     * The same rule for where an injection lands, not just which method it
     * lands in.
     *
     * <p>An {@code @At} target names a member of whatever class the injected
     * code calls into, and when that class is vanilla the name only survives
     * through the refmap. Opting out of remapping there leaves the readable
     * name against an obfuscated game — the injection point resolves to
     * nothing, and the enclosing injector fails exactly as a bad selector
     * would. Nothing in the codebase does this today; the rule is here so that
     * staying that way is checked rather than remembered.
     */
    static void verify(
            List<Path> configs,
            MixinProductionNamesVerification.VanillaNames vanilla
    ) throws Exception {
        for (Path config : configs) {
            JsonObject json = MixinProductionNamesVerification.read(config);
            for (String mixinName : MixinProductionNamesVerification.mixinClassNames(json)) {
                Class<?> mixin = Class.forName(
                        mixinName,
                        false,
                        MixinInjectionPoints.class.getClassLoader()
                );
                boolean classRemaps = MixinProductionNamesVerification.remapFlag(mixin.getAnnotations(), true);
                for (Method method : mixin.getDeclaredMethods()) {
                    for (Annotation annotation : method.getAnnotations()) {
                        for (Object point : injectionPointsOf(annotation)) {
                            checkInjectionPoint(
                                    config,
                                    mixinName,
                                    method.getName(),
                                    point,
                                    classRemaps,
                                    vanilla
                            );
                        }
                    }
                }
            }
        }
    }

    private static void checkInjectionPoint(
            Path config,
            String mixinName,
            String handler,
            Object point,
            boolean classRemaps,
            MixinProductionNamesVerification.VanillaNames vanilla
    ) throws ReflectiveOperationException {
        String target = (String) ((Annotation) point).annotationType()
                .getDeclaredMethod("target")
                .invoke(point);
        if (target == null || target.isBlank()) {
            return;
        }
        boolean remapped = classRemaps
                && MixinProductionNamesVerification.remapFlag(new Annotation[]{(Annotation) point}, true);
        if (remapped) {
            return;
        }
        int semicolon = target.indexOf(';');
        int parenthesis = target.indexOf('(');
        if (semicolon < 0 || parenthesis < semicolon) {
            return;
        }
        String owner = target.substring(0, semicolon + 1);
        if (!owner.startsWith("Lnet/minecraft/")) {
            return;
        }
        String member = target.substring(semicolon + 1);
        String name = MixinProductionNamesVerification.nameOf(member);
        if (MixinProductionNamesVerification.looksObfuscated(name)) {
            return;
        }
        int memberParenthesis = member.indexOf('(');
        Set<String> obfuscated = memberParenthesis < 0
                ? vanilla.byName(name)
                : vanilla.byNameAndDescriptor(
                        name, member.substring(memberParenthesis));
        MixinProductionNamesVerification.require(
                obfuscated.isEmpty(),
                config.getFileName() + ":" + MixinProductionNamesVerification.simpleName(mixinName)
                        + "#" + handler + " points at '" + target
                        + "' without remapping, but vanilla renames that "
                        + "member to " + obfuscated + " in the game players "
                        + "run. Drop remap = false on the @At, or write the "
                        + "obfuscated name."
        );
    }

    /**
     * Every {@code @At} an injector carries, including the pair bounding each
     * of its slices. Empty when the annotation is not an injector.
     */
    private static List<Object> injectionPointsOf(Annotation annotation) {
        List<Object> points = new ArrayList<>();
        collect(annotation, "at", points);
        List<Object> slices = new ArrayList<>();
        collect(annotation, "slice", slices);
        for (Object slice : slices) {
            collect((Annotation) slice, "from", points);
            collect((Annotation) slice, "to", points);
        }
        return points;
    }

    private static void collect(
            Annotation annotation,
            String accessor,
            List<Object> into
    ) {
        try {
            Object value = annotation.annotationType()
                    .getDeclaredMethod(accessor)
                    .invoke(annotation);
            if (value instanceof Object[] many) {
                into.addAll(List.of(many));
            } else if (value != null) {
                into.add(value);
            }
        } catch (ReflectiveOperationException absent) {
            // Not an annotation that carries this member.
        }
    }
}
