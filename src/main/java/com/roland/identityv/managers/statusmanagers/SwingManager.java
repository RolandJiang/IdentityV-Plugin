package com.roland.identityv.managers.statusmanagers;

import com.roland.identityv.core.IdentityV;

public class SwingManager extends ActionManager {
    public SwingManager(IdentityV plugin) {
        super(plugin);
        instance = this;
    }

    public static SwingManager instance;

    public static SwingManager getInstance() {
        return instance;
    }
}
