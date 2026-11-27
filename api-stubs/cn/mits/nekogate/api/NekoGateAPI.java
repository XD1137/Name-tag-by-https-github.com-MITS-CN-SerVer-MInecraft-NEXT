package cn.mits.nekogate.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface NekoGateAPI {
    int API_VERSION = 1;
    int API_MINOR_VERSION = 2;
    Path dataDirectory();
    EventBus events();
    PlayerService players();
    AuditService audit();
    CommandRegistry commands();
    PermissionService permissions();
    DisplayNameService displayNames();

    interface EventBus {
        <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener);
    }
    interface Subscription extends AutoCloseable { @Override void close(); }
    interface PlayerService {
        List<Player> onlinePlayers();
        Optional<Player> find(String nameOrUuid);
        boolean sendMessage(String playerName, String message);
        boolean kick(String playerName, String reason);
    }
    record Player(UUID uuid, String name) { }
    interface AuditService { void log(String eventType, Map<String, ?> fields); }
    interface CommandRegistry {
        Subscription register(String name, String permission, String description, CommandExecutor executor);
    }
    @FunctionalInterface interface CommandExecutor { void execute(CommandContext context) throws Exception; }
    interface CommandContext {
        Player player();
        List<String> arguments();
        String input();
        void reply(String message);
    }
    interface PermissionService { boolean has(Player player, String permission); }
}
