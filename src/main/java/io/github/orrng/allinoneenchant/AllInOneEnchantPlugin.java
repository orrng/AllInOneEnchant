package io.github.orrng.allinoneenchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AllInOneEnchantPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Long> lastWarnedAt = new HashMap<>();
    private LegacyComponentSerializer legacy;

    private int maxEnchantLevel;
    private boolean allowUnsafeEnchantments;
    private boolean allowConflictingEnchantments;
    private boolean blockOverLimit;
    private boolean bypassTooExpensive;
    private boolean resetPriorWorkPenalty;
    private int baseAnvilCost;
    private int costPerEnchantLevel;
    private int maximumAnvilCost;
    private long messageCooldownMillis;

    @Override
    public void onEnable() {
        legacy = LegacyComponentSerializer.legacyAmpersand();
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("aioenchants.reload")) {
                sender.sendMessage(color(message("no-permission")));
                return true;
            }
            reloadConfig();
            loadSettings();
            sender.sendMessage(color(message("reloaded")));
            return true;
        }
        sender.sendMessage(color("&e/" + label + " reload"));
        return true;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);
        if (isEmpty(left) || isEmpty(right)) {
            return;
        }

        Map<Enchantment, Integer> incoming = readEnchantments(right);
        if (incoming.isEmpty()) {
            return;
        }

        HumanEntity viewer = event.getView().getPlayer();
        boolean bypass = viewer.hasPermission("aioenchants.bypass");
        BuildResult build = buildResult(left, incoming, event.getView(), bypass);

        if (!build.allowed) {
            event.setResult(null);
            warn(viewer, build.message);
            return;
        }

        AnvilView view = event.getView();
        int cost = Math.max(0, Math.min(maximumAnvilCost, build.cost));
        if (bypassTooExpensive) {
            view.setMaximumRepairCost(Math.max(maximumAnvilCost, cost));
        }
        view.setRepairCost(cost);
        event.setResult(build.item);
    }

    private BuildResult buildResult(ItemStack left, Map<Enchantment, Integer> incoming, AnvilView view, boolean bypass) {
        ItemStack result = left.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return BuildResult.denied("&c이 아이템은 인챈트할 수 없습니다.");
        }

        int cost = baseAnvilCost;
        Map<Enchantment, Integer> current = readEnchantments(result);

        for (Map.Entry<Enchantment, Integer> entry : incoming.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int incomingLevel = Math.max(1, entry.getValue());

            if (!bypass && !allowUnsafeEnchantments && !enchantment.canEnchantItem(left)) {
                return BuildResult.denied(formatMessage("incompatible", enchantment, incomingLevel));
            }

            if (!bypass && !allowConflictingEnchantments && hasConflict(current, enchantment)) {
                return BuildResult.denied(formatMessage("conflict", enchantment, incomingLevel));
            }

            int oldLevel = current.getOrDefault(enchantment, 0);
            int finalLevel = oldLevel == incomingLevel ? oldLevel + 1 : Math.max(oldLevel, incomingLevel);
            if (!bypass && finalLevel > maxEnchantLevel) {
                if (blockOverLimit) {
                    return BuildResult.denied(formatMessage("over-limit", enchantment, finalLevel));
                }
                finalLevel = maxEnchantLevel;
            }

            current.put(enchantment, finalLevel);
            cost += Math.max(1, finalLevel) * Math.max(0, costPerEnchantLevel);
        }

        if (!bypass) {
            for (Map.Entry<Enchantment, Integer> entry : current.entrySet()) {
                if (entry.getValue() > maxEnchantLevel) {
                    return BuildResult.denied(formatMessage("over-limit", entry.getKey(), entry.getValue()));
                }
            }
        }

        for (Map.Entry<Enchantment, Integer> entry : current.entrySet()) {
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            } else {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }

        applyRename(meta, view.getRenameText());

        if (resetPriorWorkPenalty && meta instanceof Repairable repairable) {
            repairable.setRepairCost(0);
        }

        result.setItemMeta(meta);
        return BuildResult.allowed(result, cost);
    }

    private Map<Enchantment, Integer> readEnchantments(ItemStack item) {
        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchantments.putAll(storageMeta.getStoredEnchants());
        }
        enchantments.putAll(item.getEnchantments());
        return enchantments;
    }

    private boolean hasConflict(Map<Enchantment, Integer> current, Enchantment incoming) {
        for (Enchantment existing : current.keySet()) {
            if (!existing.equals(incoming) && existing.conflictsWith(incoming)) {
                return true;
            }
        }
        return false;
    }

    private void applyRename(ItemMeta meta, String renameText) {
        if (renameText == null) {
            return;
        }
        String trimmed = renameText.trim();
        if (trimmed.isEmpty()) {
            meta.setDisplayName(null);
            return;
        }
        meta.setDisplayName(trimmed);
    }

    private void warn(HumanEntity viewer, String rawMessage) {
        if (!(viewer instanceof Player player) || rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastWarnedAt.get(player.getUniqueId());
        if (last != null && now - last < messageCooldownMillis) {
            return;
        }
        lastWarnedAt.put(player.getUniqueId(), now);
        Component message = legacy.deserialize(message("prefix") + rawMessage);
        player.sendActionBar(message);
        player.sendMessage(message);
    }

    private void loadSettings() {
        maxEnchantLevel = Math.max(1, getConfig().getInt("max-enchantment-level", 15));
        allowUnsafeEnchantments = getConfig().getBoolean("allow-unsafe-enchantments", true);
        allowConflictingEnchantments = getConfig().getBoolean("allow-conflicting-enchantments", true);
        blockOverLimit = getConfig().getBoolean("block-over-limit", true);
        bypassTooExpensive = getConfig().getBoolean("bypass-too-expensive", true);
        resetPriorWorkPenalty = getConfig().getBoolean("reset-prior-work-penalty", true);
        baseAnvilCost = Math.max(0, getConfig().getInt("base-anvil-cost", 1));
        costPerEnchantLevel = Math.max(0, getConfig().getInt("cost-per-enchant-level", 1));
        maximumAnvilCost = Math.max(0, getConfig().getInt("maximum-anvil-cost", 255));
        messageCooldownMillis = Math.max(0L, getConfig().getLong("message-cooldown-millis", 1200L));
    }

    private String formatMessage(String path, Enchantment enchantment, int level) {
        return message(path)
            .replace("%enchant%", readableEnchantName(enchantment))
            .replace("%level%", String.valueOf(level))
            .replace("%max%", String.valueOf(maxEnchantLevel));
    }

    private String readableEnchantName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        String[] parts = key.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String message(String path) {
        return getConfig().getString("messages." + path, "");
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private record BuildResult(boolean allowed, ItemStack item, int cost, String message) {
        static BuildResult allowed(ItemStack item, int cost) {
            return new BuildResult(true, item, cost, null);
        }

        static BuildResult denied(String message) {
            return new BuildResult(false, null, 0, message);
        }
    }
}
