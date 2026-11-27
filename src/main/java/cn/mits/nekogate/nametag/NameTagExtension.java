package cn.mits.nekogate.nametag;

import cn.mits.nekogate.api.NekoGateAPI;
import cn.mits.nekogate.api.NekoGateExtension;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Persistent player presentation-name management for NekoGate.
 * Authenticated usernames and UUIDs are never modified.
 */
public final class NameTagExtension implements NekoGateExtension {
    private static final String PERMISSION = "nekogate.nametag";
    private static final Path CONFIG_FILE = Path.of("./data/name.josn5");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final AtomicBoolean enabled = new AtomicBoolean();
    private NekoGateAPI api;
    private NekoGateAPI.Subscription commandSubscription;

    @Override
    public void onEnable(NekoGateAPI api) throws Exception {
        if (api == null) throw new IllegalArgumentException("NekoGate API is required");
        if (NekoGateAPI.API_VERSION != 1 || NekoGateAPI.API_MINOR_VERSION < 2) {
            throw new IllegalStateException("NameTag requires NekoGate Extension API v1.2 or newer");
        }
        this.api = api;
        api.displayNames().reload();
        this.commandSubscription = api.commands().register(
                "nametag", PERMISSION, "Manage player display names", this::execute);
        this.enabled.set(true);
        System.out.println("[NameTag] Enabled (API " + NekoGateAPI.API_VERSION + "." +
                NekoGateAPI.API_MINOR_VERSION + "), config: " + CONFIG_FILE.toAbsolutePath());
    }

    @Override
    public void onDisable() {
        this.enabled.set(false);
        NekoGateAPI.Subscription subscription = this.commandSubscription;
        this.commandSubscription = null;
        if (subscription != null) subscription.close();
        this.api = null;
    }

    private void execute(NekoGateAPI.CommandContext context) throws Exception {
        if (!this.enabled.get()) return;
        var args = context.arguments();
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("help")) {
            help(context);
            return;
        }

        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                requireArgs(args, 1, "Usage: /nametag reload");
                this.api.displayNames().reload();
                context.reply("§a[NameTag] Configuration reloaded.");
            }
            case "set" -> {
                requireArgs(args, 3, "Usage: /nametag set <player|uuid> <display name>");
                UUID id = resolveTarget(args.get(1));
                String name = join(args, 2);
                mutate(id, player -> player.add("name", textComponent(name)));
                context.reply("§a[NameTag] Display name saved for " + args.get(1) + ".");
            }
            case "prefix", "suffix" -> {
                requireArgs(args, 3, "Usage: /nametag " + args.get(0) + " <player|uuid> <text>");
                UUID id = resolveTarget(args.get(1));
                String field = args.get(0).toLowerCase(Locale.ROOT);
                String value = join(args, 2);
                mutate(id, player -> player.add(field, textComponent(value)));
                context.reply("§a[NameTag] " + field + " saved for " + args.get(1) + ".");
            }
            case "color" -> {
                requireArgs(args, 3, "Usage: /nametag color <player|uuid> <#RRGGBB>");
                UUID id = resolveTarget(args.get(1));
                String color = args.get(2);
                requireHex(color);
                mutate(id, player -> {
                    JsonObject name = currentName(player, args.get(1));
                    name.addProperty("color", color);
                    name.remove("gradient");
                    player.add("name", name);
                });
                context.reply("§a[NameTag] Name color saved for " + args.get(1) + ".");
            }
            case "gradient" -> {
                requireArgs(args, 5, "Usage: /nametag gradient <player|uuid> <start #RRGGBB> <end #RRGGBB> <text>");
                UUID id = resolveTarget(args.get(1));
                String start = args.get(2), end = args.get(3);
                requireHex(start);
                requireHex(end);
                String value = join(args, 4);
                mutate(id, player -> {
                    JsonObject name = new JsonObject();
                    name.addProperty("text", value);
                    com.google.gson.JsonArray colors = new com.google.gson.JsonArray();
                    colors.add(start);
                    colors.add(end);
                    name.add("gradient", colors);
                    player.add("name", name);
                });
                context.reply("§a[NameTag] Gradient saved for " + args.get(1) + ".");
            }
            case "clear" -> {
                requireArgs(args, 2, "Usage: /nametag clear <player|uuid>");
                UUID id = resolveTarget(args.get(1));
                synchronized (this) {
                    JsonObject root = readConfig();
                    JsonObject players = players(root);
                    players.remove(id.toString());
                    writeConfig(root);
                    this.api.displayNames().clear(id);
                    this.api.displayNames().reload();
                }
                audit("clear", id);
                context.reply("§a[NameTag] Display-name override removed for " + args.get(1) + ".");
            }
            default -> help(context);
        }
    }

    private void mutate(UUID id, PlayerMutation mutation) throws IOException {
        synchronized (this) {
            JsonObject root = readConfig();
            JsonObject players = players(root);
            JsonObject player = players.has(id.toString()) && players.get(id.toString()).isJsonObject()
                    ? players.getAsJsonObject(id.toString()) : new JsonObject();
            mutation.apply(player);
            players.add(id.toString(), player);
            writeConfig(root);
            // set() is runtime-only in API v1.2. Clear any runtime override, then reload
            // the persisted file so the core packet handlers immediately see this value.
            this.api.displayNames().clear(id);
            this.api.displayNames().reload();
        }
        audit("update", id);
    }

    private UUID resolveTarget(String target) {
        try {
            return UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
            return this.api.players().find(target)
                    .map(NekoGateAPI.Player::uuid)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Player is not online; use their UUID for offline players: " + target));
        }
    }

    private JsonObject readConfig() throws IOException {
        if (!Files.exists(CONFIG_FILE)) return new JsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(CONFIG_FILE));
            if (!parsed.isJsonObject()) throw new IllegalStateException("Root must be a JSON object");
            return parsed.getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException("Invalid " + CONFIG_FILE + ": " + error.getMessage(), error);
        }
    }

    private static JsonObject players(JsonObject root) {
        if (!root.has("players") || !root.get("players").isJsonObject()) {
            root.add("players", new JsonObject());
        }
        return root.getAsJsonObject("players");
    }

    private static JsonObject currentName(JsonObject player, String fallbackName) {
        JsonElement existing = player.get("name");
        JsonObject name;
        if (existing != null && existing.isJsonObject()) {
            name = existing.getAsJsonObject().deepCopy();
        } else {
            name = new JsonObject();
            name.addProperty("text", existing != null && existing.isJsonPrimitive()
                    ? existing.getAsString() : fallbackName);
        }
        return name;
    }

    private static JsonObject textComponent(String text) {
        JsonObject component = new JsonObject();
        component.addProperty("text", text);
        return component;
    }

    private void writeConfig(JsonObject root) throws IOException {
        Path absolute = CONFIG_FILE.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator());
        try {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void audit(String action, UUID id) {
        try {
            this.api.audit().log("nametag." + action, Map.of("uuid", id.toString()));
        } catch (RuntimeException ignored) {
            // Auditing must not break a successful configuration change.
        }
    }

    private static void requireArgs(java.util.List<String> args, int count, String usage) {
        if (args.size() < count) throw new IllegalArgumentException(usage);
    }

    private static void requireHex(String value) {
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Color must use #RRGGBB format: " + value);
        }
    }

    private static String join(java.util.List<String> args, int start) {
        return String.join(" ", args.subList(start, args.size()));
    }

    private static void help(NekoGateAPI.CommandContext context) {
        context.reply("§6[NameTag] §f/nametag set <player|uuid> <name>");
        context.reply("§6[NameTag] §f/nametag prefix|suffix <player|uuid> <text>");
        context.reply("§6[NameTag] §f/nametag color <player|uuid> <#RRGGBB>");
        context.reply("§6[NameTag] §f/nametag gradient <player|uuid> <start> <end> <text>");
        context.reply("§6[NameTag] §f/nametag clear <player|uuid> | reload");
    }

    @FunctionalInterface
    private interface PlayerMutation {
        void apply(JsonObject player);
    }
}
