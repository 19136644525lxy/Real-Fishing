package dev.realfishing.platform;

import java.nio.file.Path;

/**
 * 平台差异的唯一出口：加载器名、是否客户端、配置目录。
 * 不负责：客户端渲染钩子——按 SPEC §8，registerClientHooks 属于 M5，届时在此接口补充。
 */
public interface IPlatform {
    /** "fabric" 或 "forge"。 */
    String loaderName();

    /** 当前是否客户端环境（专用服务器为 false）。 */
    boolean isClient();

    /** 配置目录，config/realfishing/ 的父目录。 */
    Path configDir();
}
