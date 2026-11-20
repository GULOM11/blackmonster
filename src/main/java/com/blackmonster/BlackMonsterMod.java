package com.blackmonster;

import com.blackmonster.entity.BlackMonsterEntity;
import com.blackmonster.registry.ModEntities;
import com.blackmonster.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BlackMonsterMod.MODID)
public class BlackMonsterMod {
    public static final String MODID = "blackmonster";

    public BlackMonsterMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener(this::onAttributes);
        bus.addListener(this::onCreativeTab);
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BLACK_MONSTER.get(), BlackMonsterEntity.createAttributes().build());
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(ModItems.BLACK_MONSTER_SPAWN_EGG);
        if (event.getTabKey() == CreativeModeTabs.COMBAT) event.accept(ModItems.BLACK_MONSTER_TROPHY);
    }
}
