package mcinterface1182.mixin.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mcinterface1182.InterfaceEventsModelLoader;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packloading.PackParser;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

@Mixin(MultiPackResourceManager.class)
public abstract class MultiPackResourceManagerMixin {
    @Shadow
    private Map<String, FallbackResourceManager> namespacedManagers;
    @Shadow
    @Mutable
    private List<PackResources> packs;

    /**
     * Need this to add our packs to the fallback pack location to properly load.
     */
    @Inject(method = "<init>(Lnet/minecraft/server/packs/PackType;Ljava/util/List;)V", at = @At(value = "TAIL"))
    public void inject_init(PackType pType, List<PackResources> pPackResources, CallbackInfo ci) {
        System.out.println("ADDING " + InterfaceEventsModelLoader.packPack + " " + pType);
        List<PackResources> packs2 = new ArrayList<>();
        packs2.addAll(packs);
        packs2.add(InterfaceEventsModelLoader.packPack);
        packs = packs2;
        namespacedManagers.computeIfAbsent(InterfaceManager.coreModID, k -> new FallbackResourceManager(pType, InterfaceManager.coreModID)).add(InterfaceEventsModelLoader.packPack);
        PackParser.getAllPackIDs().forEach(packID -> namespacedManagers.computeIfAbsent(packID, k -> new FallbackResourceManager(pType, packID)).add(InterfaceEventsModelLoader.packPack));
    }
}
