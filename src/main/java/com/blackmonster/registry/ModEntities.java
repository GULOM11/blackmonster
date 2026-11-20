package com.blackmonster.registry;

import com.blackmonster.BlackMonsterMod;
import com.blackmonster.entity.BlackMonsterEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BlackMonsterMod.MODID);

    public static final RegistryObject<EntityType<BlackMonsterEntity>> BLACK_MONSTER = ENTITIES.register("black_monster",
            () -> EntityType.Builder.of(BlackMonsterEntity::new, MobCategory.MONSTER)
                    .sized(2.3F, 3.4F).fireImmune().clientTrackingRange(16)
                    .build("black_monster"));

    private ModEntities() {}
}
