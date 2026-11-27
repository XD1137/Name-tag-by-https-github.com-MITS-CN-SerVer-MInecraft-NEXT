package cn.mits.nekogate.api;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** Compile-time snapshot of the NekoGate Extension API v1.3. */
public interface DisplayNameService {
    Optional<DisplayName> find(UUID playerId);
    Optional<DisplayName> find(String username);
    void set(UUID playerId, DisplayName displayName);
    void set(String username, DisplayName displayName);
    void clear(UUID playerId);
    void clear(String username);
    void reload() throws IOException;
    Component chatName(UUID playerId, String authenticatedUsername);
    Component chatName(String username);
    Component tabName(UUID playerId, String authenticatedUsername);
    Component tabName(String username);
}
