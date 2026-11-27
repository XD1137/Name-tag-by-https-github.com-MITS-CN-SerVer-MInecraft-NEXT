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
 *
 * <p>Configuration entries are keyed by Minecraft player name, never UUID.
 * Authenticated identity is not modified.</p>
 */
public final class NameTagExtension implements NekoGateExtension {
    private static final String PERMISSION = "nekogate.nametag";
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final AtomicBoolean enabled = new AtomicBoolean();
    private NekoGateAPI api;
    private Path configFile;
    private NekoGateAPI.Subscription commandSubscription;

    @Override
    public void onEnable(NekoGateAPI api) throws Exception {
        if (api == null) throw new IllegalArgumentException("NekoGate API is required");
        this.api = api;
        this.configFile = api.dataDirectory().resolve("name.josn5");
        api.displayNames().reload();
        this.commandSubscription = api.commands().register(
                "nametag", PERMISSION, "Manage player display names", this::execute);
        this.enabled.set(true);
        System.out.println("[NameTag] Enabled (API " + NekoGateAPI.API_VERSION + "."
                + NekoGateAPI.API_MINOR_VERSION + "), config: " + this.configFile.toAbsolutePath());
    }

    @Override
    public void onDisable() {
        this.enabled.set(false);
        NekoGateAPI.Subscription subscription = this.commandSubscription;
        this.commandSubscription = null;
        if (subscription != null) subscription.close();
        this.api = null;
        this.configFile = null;
    }

    private void execute(NekoGateAPI.CommandContext context) throws Exception {
        if (!this.enabled.get()) return;
        var args = context.arguments();
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("help")) {
            help(context);
            return;
        }

        try {
            executeSubcommand(context, args);
        } catch (IllegalArgumentException error) {
            context.reply("§c[NameTag] " + error.getMessage());
        } catch (IOException error) {
            System.err.println("[NameTag] Configuration operation failed: " + error);
            context.reply("§c[NameTag] Could not update the configuration: " + error.getMessage());
        }
    }

    private void executeSubcommand(NekoGateAPI.CommandContext context, java.util.List<String> args)
            throws Exception {
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                requireArgs(args, 1, "Usage: /nametag reload");
                this.api.displayNames().reload();
                context.reply("§a[NameTag] Configuration reloaded.");
            }
            case "set" -> {
                requireArgs(args, 3, "Usage: /nametag set <player-name> <display name>");
                String playerName = resolveTarget(args.get(1));
                String name = join(args, 2);
                mutate(playerName, player -> player.add("name", textComponent(name)));
                context.reply("§a[NameTag] Display name saved for " + playerName + ".");
            }
            case "prefix", "suffix" -> {
                requireArgs(args, 3, "Usage: /nametag " + args.get(0) + " <player-name> <text>");
                String playerName = resolveTarget(args.get(1));
                String field = args.get(0).toLowerCase(Locale.ROOT);
                String value = join(args, 2);
                mutate(playerName, player -> player.add(field, textComponent(value)));
                context.reply("§a[NameTag] " + field + " saved for " + playerName + ".");
            }
            case "color" -> {
                requireArgs(args, 3, "Usage: /nametag color <player-name> <#RRGGBB>");
                String playerName = resolveTarget(args.get(1));
                String color = args.get(2);
                requireHex(color);
                mutate(playerName, player -> {
                    JsonObject name = currentName(player, playerName);
                    name.addProperty("color", color);
                    name.remove("gradient");
                    player.add("name", name);
                });
                context.reply("§a[NameTag] Name color saved for " + playerName + ".");
            }
            case "gradient" -> {
                requireArgs(args, 5,
                        "Usage: /nametag gradient <player-name> <start #RRGGBB> <end #RRGGBB> <text>");
                String playerName = resolveTarget(args.get(1));
                String start = args.get(2), end = args.get(3);
                requireHex(start);
                requireHex(end);
                String value = join(args, 4);
                mutate(playerName, player -> {
                    JsonObject name = new JsonObject();
                    name.addProperty("text", value);
                    com.google.gson.JsonArray colors = new com.google.gson.JsonArray();
                    colors.add(start);
                    colors.add(end);
                    name.add("gradient", colors);
                    player.add("name", name);
                });
                context.reply("§a[NameTag] Gradient saved for " + playerName + ".");
            }
            case "clear" -> {
                requireArgs(args, 2, "Usage: /nametag clear <player-name>");
                String playerName = resolveTarget(args.get(1));
                synchronized (this) {
                    JsonObject root = readConfig();
                    entries(root).remove(playerName);
                    writeConfig(root);
                    this.api.displayNames().clear(playerName);
                    this.api.displayNames().reload();
                }
                audit("clear", playerName);
                context.reply("§a[NameTag] Display-name override removed for " + playerName + ".");
            }
            default -> help(context);
        }
    }

    private void mutate(String playerName, PlayerMutation mutation) throws IOException {
        synchronized (this) {
            JsonObject root = readConfig();
            JsonObject entries = entries(root);
            JsonObject player = entries.has(playerName) && entries.get(playerName).isJsonObject()
                    ? entries.getAsJsonObject(playerName) : new JsonObject();
            mutation.apply(player);
            entries.add(playerName, player);
            writeConfig(root);

            // Persisted configuration is authoritative; remove any transient API override
            // before reloading the name-keyed entries from disk.
            this.api.displayNames().clear(playerName);
            this.api.displayNames().reload();
        }
        audit("update", playerName);
    }

    private String resolveTarget(String target) {
        if (target == null || !PLAYER_NAME.matcher(target).matches()) {
            throw new IllegalArgumentException("Use a Minecraft player name (1-16 letters, digits or _).");
        }

        // Prefer the exact spelling reported by the live player profile.
        return this.api.players().find(target)
                .map(NekoGateAPI.Player::name)
                .filter(name -> name != null && PLAYER_NAME.matcher(name).matches())
                .orElse(target);
    }

    private JsonObject readConfig() throws IOException {
        if (!Files.exists(this.configFile)) return new JsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(this.configFile));
            if (!parsed.isJsonObject()) throw new IllegalStateException("Root must be a JSON object");
            return parsed.getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException("Invalid " + this.configFile + ": " + error.getMessage(), error);
        }
    }

    /**
     * The core accepts both {"players": {...}} and a root-level player map.
     * Keep the existing shape instead of silently moving or discarding old entries.
     */
    private static JsonObject entries(JsonObject root) throws IOException {
        if (!root.has("players")) return root;
        if (!root.get("players").isJsonObject()) {
            throw new IOException("The 'players' property must be a JSON object");
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
        Path absolute = this.configFile.toAbsolutePath();
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

    private void audit(String action, String playerName) {
        try {
            this.api.audit().log("nametag." + action, Map.of("name", playerName));
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
        context.reply("§6[NameTag] §f/nametag set <player-name> <name>");
        context.reply("§6[NameTag] §f/nametag prefix|suffix <player-name> <text>");
        context.reply("§6[NameTag] §f/nametag color <player-name> <#RRGGBB>");
        context.reply("§6[NameTag] §f/nametag gradient <player-name> <start> <end> <text>");
        context.reply("§6[NameTag] §f/nametag clear <player-name> | reload");
    }

    @FunctionalInterface
    private interface PlayerMutation {
        void apply(JsonObject player);
    }
}
