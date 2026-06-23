package cn.wubo.dynamo.spring.demo;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 反射调用相关的工具方法。
 *
 * <p>CompileController / AspectController / RuntimeController 都用：
 * <ul>
 *   <li>{@link #findMethod} 按名称 + 参数数量查找 {@link Method}，并 setAccessible(true)</li>
 *   <li>{@link #coerceArgs} 把 JSON 数组里的字符串按目标形参类型转换（支持基本类型 + String）</li>
 * </ul>
 */
final class ArgsHelper {

    private ArgsHelper() {}

    /**
     * 在 {@code clazz} 上按名称 + 形参数量找方法；找到多个取第一个。
     * 找到后 setAccessible(true)。
     */
    static Method findMethod(Class<?> clazz, String name, int argCount) {
        Method match = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isBridge() || m.isSynthetic()) continue;
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != argCount) continue;
            if (match == null) match = m;
        }
        if (match != null) {
            match.setAccessible(true);
        }
        return match;
    }

    /**
     * 把 JSON 数组里的字符串按目标形参类型转换。简单支持基本类型 + String + Character；
     * 其它类型原样返回（让 JVM 在 {@code Method.invoke} 阶段处理）。
     */
    static Object[] coerceArgs(Class<?>[] paramTypes, List<?> raw) {
        if (paramTypes.length == 0) return new Object[0];
        Object[] out = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Object v = (raw == null || i >= raw.size()) ? null : raw.get(i);
            out[i] = coerce(paramTypes[i], v);
        }
        return out;
    }

    private static Object coerce(Class<?> target, Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw);
        try {
            if (target == String.class) return s;
            if (target == int.class || target == Integer.class) return Integer.parseInt(s);
            if (target == long.class || target == Long.class) return Long.parseLong(s);
            if (target == short.class || target == Short.class) return Short.parseShort(s);
            if (target == byte.class || target == Byte.class) return Byte.parseByte(s);
            if (target == double.class || target == Double.class) return Double.parseDouble(s);
            if (target == float.class || target == Float.class) return Float.parseFloat(s);
            if (target == boolean.class || target == Boolean.class) return Boolean.parseBoolean(s);
            if (target == char.class || target == Character.class) return s.isEmpty() ? '\0' : s.charAt(0);
            // 其它类型：原样返回
            return raw;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "参数无法转换为 " + target.getName() + ": '" + s + "'");
        }
    }
}
