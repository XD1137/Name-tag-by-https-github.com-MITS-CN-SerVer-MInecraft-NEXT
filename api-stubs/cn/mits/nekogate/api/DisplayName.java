package cn.mits.nekogate.api;

import java.util.Objects;

public record DisplayName(Component prefix, Component name, Component suffix) {
    public DisplayName {
        prefix = Objects.requireNonNullElseGet(prefix, Component::empty);
        name = Objects.requireNonNullElseGet(name, Component::empty);
        suffix = Objects.requireNonNullElseGet(suffix, Component::empty);
    }
    public static DisplayName of(String prefix, String name, String suffix) {
        return new DisplayName(Component.text(prefix), Component.text(name), Component.text(suffix));
    }
}
