package com.blackmonster.registry;

import com.blackmonster.BlackMonsterMod;
import com.blackmonster.item.TrophyItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BlackMonsterMod.MODID);

    /** Spawn egg: kulrang teri (#5B584E) + qahrabo ko'z (#B87322). */
    public static final RegistryObject<Item> BLACK_MONSTER_SPAWN_EGG = ITEMS.register("black_monster_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.BLACK_MONSTER, 0x262E35, 0xDCE0E2, new Item.Properties()));

    /** Boss kubogi - silliq OBJ modelning kichik nusxasi. */
    public static final RegistryObject<Item> BLACK_MONSTER_TROPHY = ITEMS.register("black_monster_trophy",
            () -> new TrophyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    private ModItems() {}
}
