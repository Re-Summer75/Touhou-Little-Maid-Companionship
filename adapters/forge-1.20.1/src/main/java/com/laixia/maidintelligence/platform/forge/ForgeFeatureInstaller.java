package com.laixia.maidintelligence.platform.forge;

/**
 * Version-adapter hook that installs one feature into Forge lifecycle buses.
 */
public interface ForgeFeatureInstaller {
    void install(ForgeLifecycle lifecycle);
}
