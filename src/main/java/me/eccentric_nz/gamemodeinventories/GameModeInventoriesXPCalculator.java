package me.eccentric_nz.gamemodeinventories;

import com.google.common.base.Preconditions;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import org.bukkit.entity.Player;

// By desht, adapted from ExperienceUtils (ScrollingMenuSign); credit to nisovin and comphenix
// for an approach that avoids getTotalExperience(), which breaks after a player enchants.
public class GameModeInventoriesXPCalculator {

    // this is to stop the lookup table growing without control
    private static int hardMaxLevel = 100000;

    private static int xpTotalToReachLevel[];

    static {

        // 25 is an arbitrary value for the initial table size - the actual
        // value isn't critically important since the table is resized as needed.
        initLookupTables(25);

    }

    private final WeakReference<Player> player;
    private final String playerName;

    GameModeInventoriesXPCalculator(Player player) {

        Preconditions.checkNotNull(player, "Player cannot be null");
        this.player = new WeakReference<>(player);
        playerName = player.getName();

    }

    public static int getHardMaxLevel() {

        return hardMaxLevel;

    }

    public static void setHardMaxLevel(int hardMaxLevel) {

        GameModeInventoriesXPCalculator.hardMaxLevel = hardMaxLevel;

    }

    // XP formulas from http://minecraft.gamepedia.com/Experience
    private static void initLookupTables(int maxLevel) {

        xpTotalToReachLevel = new int[maxLevel];

        for (int i = 0; i < xpTotalToReachLevel.length; i++) {

            xpTotalToReachLevel[i] = i >= 30 ? (int) (3.5 * i * i - 151.5 * i + 2220)
                    : i >= 16 ? (int) (1.5 * i * i - 29.5 * i + 360) : 17 * i;

        }

    }

    // Calculates the level for an XP quantity without the lookup tables, for
    // getLevelForExp() calls with XP beyond the range of the existing tables.
    private static int calculateLevelForExp(int exp) {

        int level = 0;
        int curExp = 7; // level 1
        int incr = 10;

        while (curExp <= exp) {

            curExp += incr;
            level++;
            incr += (level % 2 == 0) ? 3 : 4;

        }

        return level;

    }

    // Throws IllegalStateException if the player is no longer online.
    public Player getPlayer() {

        Player p = player.get();
        if (p == null) {

            throw new IllegalStateException("Player " + playerName + " is not online");

        }

        return p;

    }

    // Adjusts the player's XP by the given amount (may be negative); works around
    // non-intuitive behaviour of the basic Bukkit player.giveExp() method.
    public void changeExp(int amt) {

        changeExp((double) amt);

    }

    // As changeExp(int), for fractional amounts.
    private void changeExp(double amt) {

        setExp(getCurrentFractionalXP(), amt);

    }

    // Sets the player's experience; amount should not be negative.
    void setExp(int amt) {

        setExp(0, amt);

    }

    // Sets the player's fractional experience; amount should not be negative.
    public void setExp(double amt) {

        setExp(0, amt);

    }

    private void setExp(double base, double amt) {

        int xp = (int) Math.max(base + amt, 0);

        Player p = getPlayer();
        int curLvl = p.getLevel();
        int newLvl = getLevelForExp(xp);

        if (curLvl != newLvl) {

            p.setLevel(newLvl);

        }

        // Keep the vanilla counter equal to the absolute total being applied
        p.setTotalExperience(xp);

        double pct = (base - getXpForLevel(newLvl) + amt) / (double) (getXpNeededToLevelUp(newLvl));
        p.setExp((float) pct);

    }

    int getCurrentExp() {

        Player p = getPlayer();

        int lvl = p.getLevel();
        int cur = getXpForLevel(lvl) + Math.round(getXpNeededToLevelUp(lvl) * p.getExp());
        return cur;

    }

    private double getCurrentFractionalXP() {

        Player p = getPlayer();

        int lvl = p.getLevel();
        double cur = getXpForLevel(lvl) + (double) (getXpNeededToLevelUp(lvl) * p.getExp());
        return cur;

    }

    public boolean hasExp(int amt) {

        return getCurrentExp() >= amt;

    }

    public boolean hasExp(double amt) {

        return getCurrentFractionalXP() >= amt;

    }

    // Returns the level that a player with this amount of total XP would be.
    private int getLevelForExp(int exp) {

        if (exp <= 0) {

            return 0;

        }

        if (exp > xpTotalToReachLevel[xpTotalToReachLevel.length - 1]) {

            // need to extend the lookup tables
            int newMax = calculateLevelForExp(exp) * 2;
            Preconditions.checkArgument(newMax <= hardMaxLevel,
                    "Level for exp " + exp + " > hard max level " + hardMaxLevel);
            initLookupTables(newMax);

        }

        int pos = Arrays.binarySearch(xpTotalToReachLevel, exp);
        return pos < 0 ? -pos - 2 : pos;

    }

    // Amount of experience the XP bar can hold at the given level.
    private int getXpNeededToLevelUp(int level) {

        Preconditions.checkArgument(level >= 0, "Level may not be negative.");
        return level > 30 ? 62 + (level - 30) * 7 : level >= 16 ? 17 + (level - 15) * 3 : 17;

    }

    // Total XP needed to reach the given level.
    private int getXpForLevel(int level) {

        Preconditions.checkArgument(level >= 0 && level <= hardMaxLevel,
                "Invalid level " + level + "(must be in range 0.." + hardMaxLevel + ")");
        if (level >= xpTotalToReachLevel.length) {

            initLookupTables(level * 2);

        }

        return xpTotalToReachLevel[level];

    }

}
