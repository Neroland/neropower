package za.co.neroland.neropower;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.neropower.beam.BeamConfig;
import za.co.neroland.neropower.beam.BeamContent;
import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.data.NeroPowerDataErasure;
import za.co.neroland.neropower.environmental.EnvironmentalConfig;
import za.co.neroland.neropower.environmental.EnvironmentalContent;
import za.co.neroland.neropower.fission.FissionConfig;
import za.co.neroland.neropower.fission.FissionContent;
import za.co.neroland.neropower.link.NeroPowerLinkModule;
import za.co.neroland.neropower.registry.ModBlockEntities;
import za.co.neroland.neropower.registry.ModRegistries;
import za.co.neroland.neropower.storage.StorageConfig;
import za.co.neroland.neropower.storage.StorageContent;
import za.co.neroland.neropower.telemetry.NeroPowerTelemetry;

/**
 * Loader-agnostic entry point for NeroPower. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod
 * construction. It builds the cross-loader content registries via the
 * {@link za.co.neroland.neropower.registry.RegistrationProvider} seam and
 * registers every NeroPower machine type with NeroTech's public
 * {@link za.co.neroland.nerotech.api.MachineTypeRegistry}, so NeroTech's own loader wiring gives them
 * energy / item capabilities on every loader — NeroPower ships no capability
 * code of its own.
 *
 * <p>Timing (see {@code MachineTypeRegistry}): the registrations must land during
 * mod construction. NeroPower depends on NeroTech, so NeroTech's constructor —
 * which seeds the registry — has already run; NeoForge reads the snapshot in
 * {@code RegisterCapabilitiesEvent} (after every constructor), Fabric wires through
 * replaying listeners, Forge attaches by {@code instanceof NeroTechMachineBlockEntity}.
 */
public final class NeroPowerCommon {

    public static final String MOD_ID = "neropower";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroPower");

    private NeroPowerCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroPower] common init");
        // Own config file (config/neropower.properties) through Core's ConfigManager — never reads
        // nerotech.properties; NeroTech's balance stays NeroTech's.
        // Feature packages declare their keys on the shared schema; class-load them BEFORE Core
        // registers the schema so every key is part of neropower.properties from the first write.
        FissionConfig.init();
        StorageConfig.init();
        BeamConfig.init();
        EnvironmentalConfig.init();
        NeroPowerConfig.load();
        // Opt-out, anonymous crash reporting (Sentry, EU ingest). Right after the config load so the
        // telemetryEnabled opt-out is honoured before Sentry is ever initialised; see PRIVACY.md.
        NeroPowerTelemetry.init();
        ModRegistries.init();
        // Feature content (blocks, items, block entities, menus) — each package owns its own
        // registrations; the shared registries only aggregate.
        FissionContent.init();
        StorageContent.init();
        BeamContent.init();
        EnvironmentalContent.init();
        // Add-on seam: every NeroPower machine on NeroTech's capability surfaces. Must happen here,
        // in mod construction, so it precedes the loaders' capability events (class note).
        ModBlockEntities.registerMachineTypes();
        // Stage 8: POPIA/GDPR erasure (beam link owners, link session) on Core's shared hook, then
        // the NeroLink module (snapshots, scoped actions, targeted failure events) — last, so every
        // machine type it enumerates is registered first.
        NeroPowerDataErasure.register();
        NeroPowerLinkModule.register();
    }
}
