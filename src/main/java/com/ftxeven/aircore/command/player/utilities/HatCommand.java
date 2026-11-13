package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.ItemDisplay;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HatCommand extends BaseCommand {

    private static final String KEY = "hat";

    public HatCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        PlayerInventory inventory = player.getInventory();

        ItemStack held = inventory.getItemInMainHand();
        if (isAir(held)) {
            messenger().send(sender, configs().lang().get("utilities.hat.errors.no-item"));
            return;
        }

        ItemStack previous = inventory.getHelmet();
        if (isBound(player, previous)) {
            messenger().send(sender, configs().lang().get("utilities.hat.errors.bound"));
            return;
        }

        int amount = held.getAmount();
        ItemStack hat = held.clone();
        hat.setAmount(1);

        inventory.setHelmet(hat);
        if (amount == 1) {
            inventory.setItemInMainHand(previous); // swap
        } else {
            ItemStack rest = held.clone();
            rest.setAmount(amount - 1);
            inventory.setItemInMainHand(rest);
            giveBack(player, previous);
        }

        completeCooldown(sender, args);
        Map<String, String> placeholders = new LinkedHashMap<>();
        ItemDisplay.formatInto(placeholders, "item", hat, configs().lang());
        messenger().send(sender, configs().lang().get("utilities.hat.success"), placeholders);
    }

    private static boolean isBound(Player player, ItemStack hat) {
        return !isAir(hat)
                && player.getGameMode() != GameMode.CREATIVE
                && hat.containsEnchantment(Enchantment.BINDING_CURSE);
    }

    private static void giveBack(Player player, ItemStack item) {
        if (isAir(item)) {
            return;
        }
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}