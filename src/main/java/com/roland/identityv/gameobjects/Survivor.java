package com.roland.identityv.gameobjects;

import com.roland.identityv.actions.StruggleFree;
import com.roland.identityv.core.IdentityV;
import com.roland.identityv.enums.Action;
import com.roland.identityv.enums.Persona;
import com.roland.identityv.enums.State;
import com.roland.identityv.gameobjects.items.Controller;
import com.roland.identityv.gameobjects.items.Item;
import com.roland.identityv.handlers.SitHandler;
import com.roland.identityv.managers.gamecompmanagers.*;
import com.roland.identityv.managers.statusmanagers.CancelProtectionManager;
import com.roland.identityv.managers.statusmanagers.freeze.AttackRecoveryManager;
import com.roland.identityv.managers.statusmanagers.freeze.FreezeActionManager;
import com.roland.identityv.managers.statusmanagers.freeze.StruggleRecoveryManager;
import com.roland.identityv.utils.*;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * Survivor object (handles all events and actions involving a survivor)
 */
public class Survivor {

    public Game game;

    public Player player;
    public Hunter hunter; // for balloon
    public int state; // enum
    public int action; // enum
    public int struggleProgress;
    public IdentityV plugin;
    public int timesOnChair; // 0 is start. 1 is before half. 2 is after half (dead on chair)
    public int bleedOutTimer;
    public int chairTimer;
    public int healingProgress;
    public int rescuingProgress;
    public double cipherProgressAtStart;
    public Survivor owner;
    public Location cloneLoc;
    public Survivor healer;

    public int[] personaWeb; // tracks if they have the persona and the cooldown

    public long lastHeartbeat;
    public int selfHeal;
    public int crowsTimer;

    public Chest chest;
    public boolean wasJustHit;
    public int tideDuration;
    public boolean hitUnderTide = false;

    public Survivor target; // for heal and rescue
    public Item item;
    // Scoreboard
    public int line;

    public BukkitRunnable actionRunnable;

    public ItemStack[] armor;

    // Maybe add character here

    public Survivor(IdentityV plugin, Player player) {
        this(plugin, player, null); // Won't be able to open gates
    }

    public Survivor(IdentityV plugin, Survivor owner, Location cloneLoc, Game game) {
        // Bot
        this.plugin = plugin;
        this.game = game;
        this.player = null; // Make sure stuff checks for this

        this.owner = owner;
        this.cloneLoc = cloneLoc;

        personaWeb = new int[]{0, 0, 0, 0}; // empty
    }

    public Survivor(IdentityV plugin, Player player, Game game) {
        this.plugin = plugin;
        this.player = player;
        this.game = game;
        this.target = null;
        this.healer = null;

        actionRunnable = null;
        state = State.NORMAL;
        action = Action.NONE;
        player.setGameMode(GameMode.SURVIVAL);
        player.setMaxHealth(4);
        player.setHealthScale(4);
        player.setFoodLevel(2);
        player.setSaturation(1000);
        player.setWalkSpeed((float) Config.getDouble("attributes.survivor","walk"));
        item = null;
        player.setCanPickupItems(true);

        personaWeb = new int[]{1, 1, 30, 30}; // default persona web

        lastHeartbeat = 0;
        struggleProgress = 0;
        bleedOutTimer = 0; // config to set limit
        chairTimer = 0;
        crowsTimer = 0;
        selfHeal = 1;

        line = (SurvivorManager.getSurvivors().size()*2) + 2; // Line for their name
//        ScoreboardUtil.set("&a"+player.getDisplayName(), getNameLine());
//        ScoreboardUtil.setBar(1,"a",getBarLine());

        armor = player.getInventory().getArmorContents();
    }

    public Survivor getHealer() { return healer; }

    public void setHealer(Survivor healer) { this.healer = healer; }
    public ItemStack[] getArmor() {
        return armor;
    }

    // Only for bots
    public Survivor getOwner() {
        return owner;
    }

    public int getNameLine() {
        return line;
    }

    public int getBarLine() {
        return line-1;
    }

    public int[] getPersonaWeb() {
        return personaWeb;
    }

    public Player getPlayer() {
        return player;
    }

    public void struggle() {
        Player hunterP = hunter.getPlayer();
        if (hunter == null) return;

        struggleProgress += 1;
        player.setExp((float) struggleProgress / (float) Config.getInt("timers.survivor","struggle"));

        if (struggleProgress % Config.getInt("timers.survivor","struggle_tilt_interval") == 0) { // Make player tilt
            Console.log("Tilting "+hunterP.getDisplayName()+": "+hunterP.getLocation().getYaw());


            Location newYaw = hunterP.getLocation().clone();
            Random r = new Random();
            int chance = r.nextInt(2);
            int angle = Config.getInt("attributes.hunter","struggle_tilt_angle");
            if (chance == 0) newYaw.setYaw(newYaw.getYaw() + 60);
            else newYaw.setYaw(newYaw.getYaw() - 60);

            hunterP.eject(); // temporarily so they can teleport
            hunterP.teleport(newYaw);
        }
        if (player.getExp() == 1) {
            new StruggleFree(plugin,hunter,this);
            struggleProgress = 0;
            player.setExp(0);
            hunter = null;
        }
    }

    public void drop() {
        plugin.getServer().broadcastMessage(player.getDisplayName() + " was dropped");

        setState(State.INCAP);
        hunter = null;
        struggleProgress += 6;
        player.setExp(0);
        SitHandler.unsit(player);
    }

    public void setStruggleProgress(int struggleProgress) {
        this.struggleProgress = struggleProgress;
    }

    public Hunter getHunter() { return hunter; }

    public void setHunter(Hunter hunter) { this.hunter = hunter; }

    public void incCrowsTimer() {
        crowsTimer++;
    }

    public int getCrowsTimer() {
        return crowsTimer;
    }

    public void clearCrowsTimer() {
        crowsTimer = 0;
    }
    public boolean isVisibleToAHunter() {
        for (Hunter h : HunterManager.getHunters()) {
            if (h.getPlayer().hasLineOfSight(player)) return true;
        }
        return false;
    }
    public void incTimesOnChair() {
        timesOnChair += 1;
    }

    public int getTimesOnChair() {
        return timesOnChair;
    }

    public void incapacitate() {
//        ScoreboardUtil.set("&e"+player.getDisplayName(), getNameLine());
//        ScoreboardUtil.setBar((float)bleedOutTimer / (float) Config.getInt("timers.survivor","bleed"),"e",getBarLine());

        Animations.decreasing_ring(player.getLocation(),"animations.survivor","incap",2,40);
        //player.removePotionEffect(PotionEffectType.SPEED); // remove any speed boost
        plugin.getServer().broadcastMessage(player.getDisplayName() + " was incapacitated!");

        //IncapacitationManager.getInstance().add(player, 200); // 10 seconds for now
        healingProgress = 0;
        struggleProgress = 0;
        player.setWalkSpeed((float) Config.getDouble("attributes.survivor","incap"));
        player.setHealth(1);
        state = State.INCAP;

        player.getWorld().playEffect(player.getLocation(), Effect.CRIT, 1, 10);
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getAction() { return action; }

    public void setAction(int action) {
        clearActionRunnable();
        this.action = action;
        // Cancel Protection
        CancelProtectionManager.getInstance().add(player,10);
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public void incBleedOutTimer() {
        bleedOutTimer += 1;
        Animations.random(player.getLocation(),"animations.survivor","bleed",1.5,3);
    }

    public int getBleedOutTimer() { return bleedOutTimer;}

    public int getChairTimer() { return chairTimer;}

    public void incChairTimer() {
        chairTimer += 1;
    }

    public void death() {
        if (state != State.DEAD) {
            state = State.DEAD;
            player.setGameMode(GameMode.SPECTATOR);
            Animations.one(player.getLocation(),"animations.survivor","death",12);
            plugin.getServer().broadcastMessage(player.getDisplayName() + " died");
            SurvivorManager.checkIfOver();
        }
    }

    public int getStruggleProgress() {
        return struggleProgress;
    }

    public int getHealingProgress() {
        return healingProgress;
    }

    public void incHealingProgress(int amount) {
        incHealingProgress(amount, true);
    }

    public void incHealingProgress(int amount, boolean animate) {
        healingProgress += amount;
        if (animate) Animations.random(player.getLocation(),"animations.survivor","heal",1.5,3);
    }

    public int getRescuingProgress() { return rescuingProgress; }

    public void incRescuingProgress(int amount) {
        rescuingProgress += amount;
        Animations.ring(player.getLocation(),"animations.survivor","rescue",2);
    }

    public int getHealth() {
        return (int) player.getHealth();
    }

    public void hit(Hunter h, final int damage) {
        h.incPresence(damage);
        if (state != State.NORMAL) return; // chair

        healingProgress = 0; // Reset healing progress

        // Check for under tide
        if (tideDuration > 0) {
            h.getPlayer().sendTitle(ChatColor.RED + "Last Effort!", "");
            player.sendTitle(ChatColor.RED + "Last Effort!", "");
            hitUnderTide = true;
            // Delay damage
            new BukkitRunnable() {
                public void run() {
                    player.sendMessage("Your tide effect has worn off");
                    if (damage >= player.getHealth()) { // If they will die
                        player.damage(0.001); // for animation?
                        incapacitate();
                        return;
                    }

                    player.damage(damage);
                }
            }.runTaskLater(plugin, tideDuration * 20);


        } else {
            // Incapacitate instead of vanilla dying
            if (damage >= player.getHealth()) { // If they will die
                player.damage(0.001); // for animation?
                incapacitate();
                return;
            }

            player.damage(damage);
        }

        // If frozen, unfreeze
        if (FreezeActionManager.getInstance().isFrozen(player)) {
            FreezeActionManager.getInstance().remove(player);
            player.setLevel(0);
        }

        // Brief invulnerability
        wasJustHit = true;
        new BukkitRunnable() {
            public void run() {
                wasJustHit = false;
            }
        }.runTaskLater(plugin, 10);

        // Speed boost
        increaseSpeed(0.7,50);
        //player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Config.getInt("attributes.survivor","hit_boost_length"), 1, true, false),true);
    }

    public void startSelfHeal() {
        Console.log("Starting self heal");
        setAction(Action.SELFHEAL);

        actionRunnable = new BukkitRunnable() {
            public void run() {
                if (getAction() != Action.SELFHEAL) { // Player cleared it
                    clearActionRunnable();
                    return;
                }

                // [INCAP] Check if they can self heal and past limit
                if (getState() == State.INCAP && healingProgress >= Config.getInt("timers.survivor","self_heal_limit") && selfHeal == 0) {
                    player.sendMessage("Cannot progress anymore due to self heal limit");
                    clearActionRunnable();
                    return;
                }

                incHealingProgress(10);


                // Self revive
                if (getState() == State.INCAP) {
                    player.setExp((float) healingProgress / (float) Config.getInt("timers.survivor","revive"));
                    if (player.getExp() >= 1) { // Finished reviving
                        selfHeal -= 1;
                        player.setHealth(2);
                        player.setWalkSpeed((float) Config.getDouble("attributes.survivor","walk"));
                        setHealingProgress(0);
                        setState(State.NORMAL);
                        player.sendMessage("You have revived yourself");
                        clearActionRunnable();
                    }
                    return;
                }
            }
        };
        actionRunnable.runTaskTimer(plugin, 5, 15); // slower
    }

    public void startCloneHeal(final Survivor clone, final Survivor injured) {
        Console.log("Detected clone heal");
        setAction(Action.HEAL);
        clone.setAction(Action.GETHEAL); // Can't be injured because they are using bot
        clone.setHealer(this);

        injured.getPlayer().sendMessage("Your body is being healed");

        target = clone; // ?

        final double progressAtStart = clone.getHealingProgress(); // May need to adjust this later

        actionRunnable = new BukkitRunnable() {
            public void run() {
//                if (clone.getAction() != Action.GETHEAL) { // Player cleared it when switched back
//                    if (CalibrationManager.hasCalibration(Survivor.this)) {
//                        CalibrationManager.get(Survivor.this).finish();
//                    }
//                    clearActionRunnable();
//                    return;
//                }

                clone.incHealingProgress(10, false); // Cancel particle effects
                Animations.random(clone.getLocation(),"animations.survivor","heal",1.5,3);

                // Calib
                if (clone.getHealingProgress() - progressAtStart > 40) { // Window for calibration
                    Random r = new Random();
                    if (r.nextInt(4) == 0) {
                        if (!CalibrationManager.hasCalibration(Survivor.this)) CalibrationManager.give(Survivor.this,Action.HEAL);
                    }
                }

                // Heal
                if (clone.getState() == State.NORMAL) {
                    player.setExp((float) clone.getHealingProgress() / (float) Config.getInt("timers.survivor","heal"));

                    // Don't change exp of player
                    //injured.getPlayer().setExp((float) injured.getHealingProgress() / (float) Config.getInt("timers.survivor","heal"));
                    if (player.getExp() >= 1) { // Finished healing
                        if (CalibrationManager.hasCalibration(Survivor.this)) {
                            CalibrationManager.get(Survivor.this).finish();
                        }

                        injured.getPlayer().setHealth(injured.getPlayer().getHealth() + 2); // Increases health to be updated later
                        clone.setHealingProgress(0);
                        injured.getPlayer().sendMessage("You have been healed");
                        Console.log("Clone was healed!");
                        //injured.getPlayer().sendMessage("You have been healed");
                        clone.clearActionRunnable();
                        clearActionRunnable();
                    }
                    return;
                }
            }
        };
        actionRunnable.runTaskTimer(plugin, 5, 10);
    }

    private Location getLocation() {
        return cloneLoc;
    }

    public void startHeal(final Survivor injured) {
        Console.log("Starting heal of "+injured.getPlayer().getDisplayName());
        setAction(Action.HEAL);
        injured.setAction(Action.GETHEAL);

        target = injured;

        final double progressAtStart = injured.getHealingProgress();

        actionRunnable = new BukkitRunnable() {
            public void run() {
                if (injured.getAction() != Action.GETHEAL) { // Player cleared it
                    if (CalibrationManager.hasCalibration(Survivor.this)) {
                        CalibrationManager.get(Survivor.this).finish();
                    }
                    clearActionRunnable();
                    return;
                }
                injured.incHealingProgress(10);

                // Calib
                if (injured.getHealingProgress() - progressAtStart > 40) { // Window for calibration
                    Random r = new Random();
                    if (r.nextInt(4) == 0) {
                        if (!CalibrationManager.hasCalibration(Survivor.this)) CalibrationManager.give(Survivor.this,Action.HEAL);
                    }
                }

                // Heal
                if (injured.getState() == State.NORMAL) {
                    player.setExp((float) injured.getHealingProgress() / (float) Config.getInt("timers.survivor","heal"));
                    injured.getPlayer().setExp((float) injured.getHealingProgress() / (float) Config.getInt("timers.survivor","heal"));
                    if (player.getExp() >= 1) { // Finished healing
                        if (CalibrationManager.hasCalibration(Survivor.this)) {
                            CalibrationManager.get(Survivor.this).finish();
                        }

                        injured.getPlayer().setHealth(injured.getPlayer().getHealth() + 2);
                        injured.setHealingProgress(0);
                        injured.getPlayer().sendMessage("You have been healed");
                        injured.clearActionRunnable();
                        clearActionRunnable();
                    }
                    return;
                }
                // Revive
                if (injured.getState() == State.INCAP) {
                    player.setExp((float) injured.getHealingProgress() / (float) Config.getInt("timers.survivor","revive"));
                    injured.getPlayer().setExp((float) injured.getHealingProgress() / (float) Config.getInt("timers.survivor","revive"));
                    if (player.getExp() >= 1) { // Finished reviving
                        if (CalibrationManager.hasCalibration(Survivor.this)) {
                            CalibrationManager.get(Survivor.this).finish();
                        }

                        injured.getPlayer().setHealth(2);
                        injured.getPlayer().setWalkSpeed((float) Config.getDouble("attributes.survivor","walk"));
                        injured.setHealingProgress(0);
                        injured.setState(State.NORMAL);
                        injured.getPlayer().sendMessage("You have been revived");
                        injured.clearActionRunnable();
                        clearActionRunnable();
                    }
                    return;
                }
            }
        };
        actionRunnable.runTaskTimer(plugin, 5, 10);
    }

    public void startRescue(final Survivor chaired) {
        setAction(Action.RESCUE);
        chaired.setRescuingProgress(0); // Reset rescuing progress
        chaired.setAction(Action.GETRESCUE);

        target = chaired;

        actionRunnable = new BukkitRunnable() {
            public void run() {
                chaired.incRescuingProgress(1);

                player.setExp((float) chaired.getRescuingProgress() / (float) Config.getInt("timers.survivor","rescue"));
                chaired.getPlayer().setExp((float) chaired.getRescuingProgress() / (float) Config.getInt("timers.survivor","rescue"));

                if (player.getExp() >= 1) { // Finished rescuing
                    RocketChairManager.getChair(chaired.getPlayer()).releaseSurvivor();
                    SitHandler.unsit(chaired.getPlayer());
                    // Use tide
                    if (personaWeb[Persona.TIDE_TURNER] > 0) {
                        useTide(chaired);
                    }
                    chaired.setAction(Action.NONE);
                    chaired.getPlayer().setHealth(2);
                    chaired.getPlayer().setWalkSpeed((float) Config.getDouble("attributes.survivor","walk"));
                    chaired.setRescuingProgress(0);
                    chaired.setState(State.NORMAL);
                    chaired.getPlayer().sendMessage("You have been rescued");
                    chaired.clearActionRunnable();
                    clearActionRunnable();
                }
            }
        };
        actionRunnable.runTaskTimer(plugin, 5, 5);
    }

    // Non bot
    public void startDecode(final Cipher cipher) {
        startDecode(cipher, cipher.getProgress());
    }

    // Bot
    public void startDecode(final Cipher cipher, double progressAtStart) {
        setAction(Action.DECODE);
        Console.log("Start decode");
        //final double progressAtStart = cipher.getProgress();
        cipherProgressAtStart = progressAtStart;
        if (!cipher.getSurvivorsDecoding().contains(this)) { // Register as a decoding survivor
            cipher.addSurvivor(this);
        }

        actionRunnable = new BukkitRunnable() {
            public void run() {
                // If 5 ciphers are done
                if (game.getCiphersDone() == 5) {
                    clearActionRunnable();
                    if (CalibrationManager.hasCalibration(Survivor.this)) {
                        CalibrationManager.get(Survivor.this).finish();
                    }
                }

                // Progress depends on number of decoders
                cipher.incProgress(Adjustments.getDecodeRate(cipher.getSurvivorsDecoding().size()));

                if (cipher.getProgress() - cipherProgressAtStart > 40) { // Window for calibration
                    Random r = new Random();
                    if (r.nextInt(6) == 0) {
                        if (!CalibrationManager.hasCalibration(Survivor.this)) CalibrationManager.give(Survivor.this, Action.DECODE);
                    }
                }
                player.setExp((float) cipher.getProgress() / (float) Config.getInt("timers.survivor","decode"));
                if (player.getExp() >= 1) {
                    cipher.pop();
                    player.sendMessage("You have finished this cipher");
                    if (CalibrationManager.hasCalibration(Survivor.this)) {
                        CalibrationManager.get(Survivor.this).finish();
                    }
                    clearActionRunnable();
                }
            }
        };
        actionRunnable.runTaskTimer(plugin, 5, 10);
    }

    public void startOpen(final Gate gate) {
        if (game != null && game.getCiphersDone() < 5) return; // need to do 5 ciphers

        setAction(Action.OPEN);
        Console.log("Start open gate");
        gate.setOpener(this);

        actionRunnable = new BukkitRunnable() {
            public void run() {
                gate.incProgress(10);
                player.setExp((float) gate.getProgress() / (float) Config.getInt("timers.survivor","open_gate"));
                if (player.getExp() >= 1) {
                    gate.open();
                    player.sendMessage("You have opened the gate");
                    clearActionRunnable();
                }
            }
        };
        actionRunnable.runTaskTimer(plugin, 5, 10);
    }

    private void setRescuingProgress(int rescuingProgress) {
        this.rescuingProgress = rescuingProgress;
    }

    public void setHealingProgress(int healingProgress) {
        this.healingProgress = healingProgress;
    }

    public BukkitRunnable getActionRunnable() {
        return actionRunnable;
    }
    public void setActionRunnable(BukkitRunnable actionRunnable) {
        this.actionRunnable = actionRunnable;
    }

    public boolean clearActionRunnable() {
        return clearActionRunnable(false);
    }

    public boolean clearActionRunnable(boolean isBotSwitch) {
        if (player != null)  player.setExp(0);

        if (action == Action.OPENCHEST) {
            if (!chest.isEmpty()) {
                // Player tried to cancel
                chest.animateOpenAndClose();
                FreezeActionManager.getInstance().add(player, Config.getInt("timers.survivor", "open_and_close_chest_duration"));
            }
            chest.setOpener(null);
            chest = null; // TODO needs testing with attacking
        }

        action = Action.NONE;
        if (actionRunnable != null) {
            if (!isBotSwitch && target != null) { // So switching from bot doesn't stop heal
                target.clearActionRunnable(true);
                target = null;
            }
            actionRunnable.cancel();
            actionRunnable = null;
            return true;
        }
        // Clear from decoding any ciphers
        CipherManager.removeDecodingSurvivor(this);
        GateManager.removeOpeningSurvivor(this);
        return false;
    }

    public int getSelfHeal() {
        return selfHeal;
    }

    public void escape() {
        state = State.ESCAPE;
        player.setGameMode(GameMode.SPECTATOR);
        //Animations.one(player.getLocation(),"animations.survivor","escape",12);
        plugin.getServer().broadcastMessage(player.getDisplayName() + " escaped");
        SurvivorManager.checkIfOver();
    }

    public boolean wasJustHit() {
        return wasJustHit;
    }

    public Survivor getTarget() {
        return target;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        // Can only pick up if they have no item TODO needs fix
        if (item == null) {
            //player.sendMessage("You can pickup items now");
            //player.setCanPickupItems(true);
        } else {
            //player.sendMessage("You cannot pickup items now");
            //player.setCanPickupItems(false);
        }
        this.item = item;
    }

    public void setCipherProgressAtStart(double cipherProgressAtStart) {
        this.cipherProgressAtStart = cipherProgressAtStart;
    }

    public double getCipherProgressAtStart() {
        return cipherProgressAtStart;
    }

    public void startOpenChest(final Chest chest) {
        setAction(Action.OPENCHEST);
        Console.log("Start open chest");
        chest.setOpener(this);
        this.chest = chest;

        // Freeze for 2 second
        chest.animateOpenAndClose();
        FreezeActionManager.getInstance().add(player,Config.getInt("timers.survivor","open_and_close_chest_duration"));

        actionRunnable = new BukkitRunnable() {
            public void run() {
                chest.incProgress(10);
                player.setExp((float) chest.getProgress() / (float) Config.getInt("timers.survivor","open_chest"));
                if (player.getExp() >= 1) {
                    chest.open();
                    player.sendMessage("You have opened the chest");
                    clearActionRunnable();
                }
            }
        };
        actionRunnable.runTaskTimer(plugin, Config.getInt("timers.survivor","open_and_close_chest_duration") + 5, 10);

    }

    // Returns if this survivor is actually a robot (can't be healed or open chests)
    public boolean isControllingRobot() {
        if (item != null && item instanceof Controller) {
            Controller cont = (Controller) item;
            if (cont.isRobot) {
                return true;
            }
        }
        return false;
    }


    public void increaseSpeed(double percentage, int duration) {
        Console.log("Increasing speed by "+percentage);
        final double amount = Config.getDouble("attributes.survivor","walk") * percentage;
        player.setWalkSpeed((float) (player.getWalkSpeed() + amount));
        new BukkitRunnable() {
            public void run() {
                player.setWalkSpeed((float) (player.getWalkSpeed() - amount));
            }
        }.runTaskLater(plugin,duration);
    }

    public void boostsCD(final int boostType) {
        personaWeb[boostType] = 0;
        new BukkitRunnable() {
            public void run() {
                personaWeb[boostType]++;
                if (personaWeb[boostType] == Config.getInt("attributes.survivor","boost_cd") / 20) {
                    player.sendMessage("Your boost is now available!");
                    cancel();
                }
            }
        }.runTaskTimer(plugin,0,20);
    }

    public int getTideDuration() {
        return tideDuration;
    }

    public void setTideDuration(int tideDuration) {
        this.tideDuration = tideDuration;
    }

    public void useTide(final Survivor rescued) {
        Console.log("Using tide");
        personaWeb[Persona.TIDE_TURNER] = 0;
        tideDuration = Config.getInt("attributes.survivor","tide_length") / 20;
        rescued.setTideDuration(tideDuration); // Manage rescued's tide duration too
        new BukkitRunnable() {
            public void run() {
                tideDuration--;
                rescued.setTideDuration(tideDuration);
                if (tideDuration == 0) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin,0,20);
    }

    public boolean wasHitUnderTide() {
        return hitUnderTide;
    }

//    public void setHitUnderTide(boolean hitUnderTide) {
//        this.hitUnderTide = hitUnderTide;
//    }
}

