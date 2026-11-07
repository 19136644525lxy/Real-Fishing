package dev.realfishing.platform;

/**
 * IPlatform 的静态持有者。平台入口必须先调用 RealFishing.init(...)，common 才能取到实现。
 * 不负责：发现/加载逻辑（不做 ServiceLoader，避免 shaded 环境下失效）。
 */
public final class Platform {
    private static IPlatform instance;

    private Platform() {
    }

    public static void set(IPlatform platform) {
        if (instance != null) {
            throw new IllegalStateException("Platform 重复初始化：" + instance.loaderName() + " -> " + platform.loaderName());
        }
        instance = platform;
    }

    public static IPlatform get() {
        if (instance == null) {
            throw new IllegalStateException("Platform 尚未初始化：平台入口必须先调用 RealFishing.init(...)");
        }
        return instance;
    }
}
