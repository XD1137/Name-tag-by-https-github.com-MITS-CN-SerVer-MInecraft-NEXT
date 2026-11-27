package cn.mits.nekogate.api;

public interface NekoGateExtension {
    void onEnable(NekoGateAPI api) throws Exception;
    default void onDisable() throws Exception { }
}
