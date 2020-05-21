package com.roland.identityv.actions;

import com.roland.identityv.core.IdentityV;
import com.roland.identityv.managers.statusmanagers.freeze.DestroyPalletManager;
import com.roland.identityv.utils.Animations;
import com.roland.identityv.utils.Config;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class DestroyPallet {

    public IdentityV plugin;

    /**
     * Initiates the action when a hunter destroys a pallet
     * @param plugin
     * @param block
     * @param p
     */
    public DestroyPallet(IdentityV plugin, final Block block, final Player p) {
        this.plugin = plugin;

        int destroyPalletTimer = Config.getInt("timers.hunter","destroy_pallet");
        DestroyPalletManager.getInstance().add(p, destroyPalletTimer);

        Animations.decreasing_ring(p.getLocation(),"animations.hunter","destroy_pallet",2.5, destroyPalletTimer);

        new BukkitRunnable() {
            public void run() {
                block.setType(Material.AIR);

                //block.getWorld().createExplosion(block.getX(), block.getY(), block.getZ(),1F,false, false);

                block.getWorld().playEffect(block.getLocation(), Effect.POTION_BREAK, 1, 10);
                cancel();
            }
        }.runTaskLater(plugin,destroyPalletTimer);
    }
}