package cn.mits.nekogate.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record Component(String text, String color, boolean bold, boolean italic, List<Component> children) {
    public Component {
        text = Objects.requireNonNullElse(text, "");
        if (color != null && !color.matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Color must be #RRGGBB: " + color);
        children = children == null ? List.of() : List.copyOf(children);
    }
    public static Component empty() { return new Component("", null, false, false, List.of()); }
    public static Component text(String value) { return new Component(value, null, false, false, List.of()); }
    public static Component colored(String value, String color) { return new Component(value, color, false, false, List.of()); }
    public Component withBold(boolean value) { return new Component(text, color, value, italic, children); }
    public Component withItalic(boolean value) { return new Component(text, color, bold, value, children); }
    public Component withChildren(List<Component> value) { return new Component(text, color, bold, italic, value); }
    public static Component join(Component... parts) {
        List<Component> result = new ArrayList<>();
        if (parts != null) for (Component part : parts) if (part != null) result.add(part);
        return new Component("", null, false, false, result);
    }
    public static Component gradient(String value, String startHex, String endHex) {
        Objects.requireNonNull(value, "value");
        int start = parseHex(startHex), end = parseHex(endHex);
        int[] cps = value.codePoints().toArray();
        List<Component> parts = new ArrayList<>(cps.length);
        for (int i = 0; i < cps.length; i++) {
            double t = cps.length < 2 ? 0 : (double) i / (cps.length - 1);
            int rgb = interpolate(start, end, t);
            parts.add(colored(new String(Character.toChars(cps[i])), String.format("#%06X", rgb)));
        }
        return join(parts.toArray(Component[]::new));
    }
    private static int parseHex(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Color must be #RRGGBB: " + value);
        return Integer.parseInt(value.substring(1), 16);
    }
    private static int interpolate(int a, int b, double t) {
        int r=(int)Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t);
        int g=(int)Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t);
        int bl=(int)Math.round((a&255)*(1-t)+(b&255)*t);
        return (r<<16)|(g<<8)|bl;
    }
}
