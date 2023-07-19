package dev.j3fftw.litexpansion.items;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import dev.j3fftw.litexpansion.Items;
import dev.j3fftw.litexpansion.LiteXpansion;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CargoConfigurator extends SimpleSlimefunItem<ItemUseHandler> implements Listener {

    private static final Gson GSON = new Gson();

    private static final NamespacedKey CARGO_BLOCK = new NamespacedKey(LiteXpansion.getInstance(), "cargo_block");
    private static final NamespacedKey CARGO_CONFIG = new NamespacedKey(LiteXpansion.getInstance(), "cargo_config");

    public CargoConfigurator() {
        super(Items.LITEXPANSION, Items.CARGO_CONFIGURATOR, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
            Items.REFINED_IRON, SlimefunItems.REINFORCED_PLATE, Items.REFINED_IRON,
            SlimefunItems.REINFORCED_PLATE, SlimefunItems.CARGO_MANAGER, SlimefunItems.REINFORCED_PLATE,
            Items.REFINED_IRON, SlimefunItems.REINFORCED_PLATE, Items.REFINED_IRON
        });

        Bukkit.getPluginManager().registerEvents(this, LiteXpansion.getInstance());
    }

    @Nonnull
    @Override
    public ItemUseHandler getItemHandler() {
        return e -> e.setUseBlock(Event.Result.DENY);
    }

    private boolean canUseCargoConfigurator(@Nonnull Player p, @Nonnull Block clicked) {
        return Slimefun.getProtectionManager().hasPermission(p, clicked, Interaction.INTERACT_BLOCK);
    }

    @EventHandler
    public void onCargoConfiguratorItemClick(PlayerInteractEvent e) {
        if (e.getItem() == null || e.getMaterial() != Material.COMPASS) {
            return;
        }

        final ItemStack clickedItem = e.getItem();

        if (!this.isItem(clickedItem) || SlimefunItem.getByItem(Items.CARGO_CONFIGURATOR).isDisabled()) {
            return;
        }

        final ItemMeta meta = clickedItem.getItemMeta();

        final List<String> defaultLore = Items.CARGO_CONFIGURATOR.getItemMetaSnapshot().getLore()
            .orElse(new ArrayList<>());
        final List<String> lore = meta.hasLore() ? meta.getLore() : defaultLore;

        // Clear the config and lore
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
            && e.getPlayer().isSneaking()
        ) {
            clearConfig(e.getPlayer(), clickedItem, meta, defaultLore, lore);
            e.setCancelled(true);
            return;
        }

        if ((e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK)
            || e.getClickedBlock() == null) {
            return;
        }

        final SlimefunItem block = BlockStorage.check(e.getClickedBlock());
        if (block == null) {
            return;
        }

        final ItemStack clickedItemStack = block.getItem();

        final String blockId = block.getId();
        if (!blockId.equals(SlimefunItems.CARGO_INPUT_NODE.getItemId())
            && !blockId.equals(SlimefunItems.CARGO_OUTPUT_NODE.getItemId())
            && !blockId.equals(SlimefunItems.CARGO_OUTPUT_NODE_2.getItemId())
        ) {
            return;
        }

        final Player p = e.getPlayer();

        if (!canUseCargoConfigurator(p, e.getClickedBlock()) && !p.hasPermission("slimefun.cargo.bypass")) {
            Slimefun.getLocalization().sendMessage(p, "inventory.no-access", true);
            return;
        }

        e.setCancelled(true);

        runActions(e, clickedItemStack, meta, blockId, lore, defaultLore);

        meta.setLore(lore);
        clickedItem.setItemMeta(meta);
    }

    private void clearConfig(@Nonnull Player player, @Nonnull ItemStack itemStack, @Nonnull ItemMeta meta,
                             @Nonnull List<String> defaultLore, @Nonnull List<String> lore
    ) {
        PersistentDataAPI.remove(meta, CARGO_BLOCK);
        PersistentDataAPI.remove(meta, CARGO_CONFIG);
        player.sendMessage(ChatColor.RED + "已清除货运节点的配置!");

        if (lore.size() != defaultLore.size()) {
            lore.clear();
            lore.addAll(defaultLore);
        }

        meta.setLore(lore);
        itemStack.setItemMeta(meta);
    }

    private void runActions(@Nonnull PlayerInteractEvent e, @Nonnull ItemStack clickedItemStack, @Nonnull ItemMeta meta,
                            @Nonnull String blockId, @Nonnull List<String> lore, @Nonnull List<String> defaultLore
    ) {
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            final String copiedBlock = PersistentDataAPI.getString(meta, CARGO_BLOCK);
            final String config = PersistentDataAPI.getString(meta, CARGO_CONFIG);
            if (copiedBlock == null || config == null) {
                e.getPlayer().sendMessage(ChatColor.RED + "你必须先复制一个节点的配置!");
                return;
            }

            if (!copiedBlock.equals(blockId)) {
                e.getPlayer().sendMessage(ChatColor.RED + "你不能复制配置到这个节点上!");
                return;
            }

            SlimefunBlockData blockData = StorageCacheUtils.getBlock(e.getClickedBlock().getLocation());
            StorageCacheUtils.executeAfterLoad(blockData, () -> {
                Map<String, String> map = GSON.fromJson(config, new TypeToken<Map<String, String>>() {}.getType());
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    blockData.setData(entry.getKey(), entry.getValue());
                }
                e.getPlayer().sendMessage(ChatColor.GREEN + "已应用配置!");
            }, false);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            SlimefunBlockData blockData = StorageCacheUtils.getBlock(e.getClickedBlock().getLocation());
            StorageCacheUtils.executeAfterLoad(blockData, () -> {
                PersistentDataAPI.setString(meta, CARGO_BLOCK, blockId);
                PersistentDataAPI.setString(meta, CARGO_CONFIG, GSON.toJson(blockData.getAllData()));

                // Has the copied part
                if (lore.size() == defaultLore.size() + 2) {
                    lore.clear();
                    lore.addAll(defaultLore);
                }
                lore.addAll(Arrays.asList("", ChatColor.GRAY + "> 物品 "
                    + ChatColor.RESET + clickedItemStack.getItemMeta().getDisplayName()
                    + ChatColor.GRAY + " 的配置"
                ));
                e.getPlayer().sendMessage(ChatColor.GREEN + "成功复制节点配置!");
            }, false);
        }
    }
}
