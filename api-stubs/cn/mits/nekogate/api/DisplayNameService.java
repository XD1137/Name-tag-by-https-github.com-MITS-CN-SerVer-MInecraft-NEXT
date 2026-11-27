package cn.mits.nekogate.api;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public interface DisplayNameService {
    Optional<DisplayName> find(UUID playerId);
    Optional<DisplayName> find(String username);
    void set(UUID playerId, DisplayName displayName);
    void clear(UUID playerId);
    void reload() throws IOException;
    Component chatName(UUID playerId, String authenticatedUsername);
    Component tabName(UUID playerId, String authenticatedUsername);
}
