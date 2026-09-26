package za.co.neroland.neropower.registry;

/**
 * Aggregates NeroPower's cross-loader content registries. Called once from
 * {@link za.co.neroland.neropower.NeroPowerCommon#init()}.
 *
 * <p>Order matters on the eager (Fabric) loader: blocks register on class-load, then
 * items (block-items need their blocks), block entities and menus (need their blocks),
 * and finally NeroPower's own creative tab, which draws the items in. On NeoForge/Forge
 * the DeferredRegisters are created here and flushed to NeroPower's mod bus by the
 * loader entry point ({@code *RegistrationFactory.registerAll(...)}).
 */
public final class ModRegistries {

    private ModRegistries() {
    }

    public static void init() {
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModMenuTypes.init();
        ModCreativeTab.init();
    }
}
