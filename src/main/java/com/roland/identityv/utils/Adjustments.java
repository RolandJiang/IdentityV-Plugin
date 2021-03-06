package com.roland.identityv.utils;

import com.roland.identityv.core.IdentityV;
import com.roland.identityv.gameobjects.Survivor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;

public class Adjustments {
    /**
     * Converts distance into a heart rate for survivor
     * @param distance
     * @return
     */
    public static int getHeartRate(double distance) {
        if (distance > 20) return 9999999;
        if (distance > 15) return 30;
        if (distance > 10) return 20;
        if (distance > 7) return 15;
        return 10;
    }

    public static int getDecodeRate(int decodersCount) {
        if (decodersCount == 1) return 10; // 10
        if (decodersCount == 2) return 8; // 16
        if (decodersCount == 3) return 7; // 21
        if (decodersCount == 4) return 6; // 24
        return 10;
    }

    public static Location spawnBlock(World world, double x, double y, double z) {
        return new Location(world, x + 0.5, y + 2, z + 0.5);
    }
}
