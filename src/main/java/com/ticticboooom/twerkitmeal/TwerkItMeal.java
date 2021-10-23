package com.ticticboooom.twerkitmeal;

import com.ticticboooom.twerkitmeal.config.CommonConfig;
import com.ticticboooom.twerkitmeal.config.TwerkConfig;
import com.ticticboooom.twerkitmeal.helper.FilterListHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;


@Mod(TwerkItMeal.MOD_ID)
public class TwerkItMeal {
    public static final String MOD_ID = "twerkitmeal";

    static final ForgeConfigSpec commonSpec;
    public static final CommonConfig COMMON_CONFIG;

    static {
        final Pair<CommonConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        commonSpec = specPair.getRight();
        COMMON_CONFIG = specPair.getLeft();
    }

    public TwerkItMeal() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, commonSpec, "twerk-config.toml");
        MinecraftForge.EVENT_BUS.register(new RegistryEvents());
    }


    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {

        private final Map<UUID, Integer> crouchCount = new HashMap<>();
        private final Map<UUID, Boolean> prevSneaking = new HashMap<>();
        private final Map<UUID, Integer> playerDistance = new HashMap<>();

        @SubscribeEvent
        public void onTwerk(TickEvent.PlayerTickEvent event) {
            if (event.player.level.isClientSide) {
                return;
            }
            UUID uuid = event.player.getUUID();
            if (!crouchCount.containsKey(uuid)){
                crouchCount.put(uuid, 0);
                prevSneaking.put(uuid, event.player.isCrouching());
                playerDistance.put(uuid, 0);
            }

            ServerLevel world = (ServerLevel) event.player.level;

            if (event.player.isSprinting() && world.getRandom().nextDouble() <= TwerkConfig.sprintGrowChance){
                triggerGrowth(event, uuid);
            }

            boolean wasPlayerSneaking = prevSneaking.get(uuid);
            int playerCrouchCount = crouchCount.get(uuid);
            if (!event.player.isCrouching()) {
                prevSneaking.put(uuid, false);
                return;
            }
            if (wasPlayerSneaking && event.player.isCrouching()) {
                return;
            } else if (!wasPlayerSneaking && event.player.isCrouching()) {
                prevSneaking.put(uuid, true);
                crouchCount.put(uuid, ++playerCrouchCount);
            }

            ServerPlayer player = (ServerPlayer) event.player;
            if (playerCrouchCount >= TwerkConfig.minCrouchesToApplyBonemeal && world.random.nextDouble() <= TwerkConfig.crouchGrowChance) {
                triggerGrowth(event, uuid);
            }
        }

        private void triggerGrowth(TickEvent.PlayerTickEvent event, UUID uuid) {
            crouchCount.put(uuid, 0);
            List<BlockPos> growables = getNearestBlocks(event.player.level, event.player.blockPosition());
            Set<BlockPos> grownDT = new HashSet<>();
            for (BlockPos growablePos : growables) {
                BlockState blockState = event.player.level.getBlockState(growablePos);
                if (!FilterListHelper.shouldAllow(blockState.getBlock().getRegistryName().toString())) {
                    continue;
                }

                if (TwerkConfig.saplingsOnly){
                    if (!BlockTags.SAPLINGS.contains(blockState.getBlock())) {
                        continue;
                    }
                }
                if (blockState.hasProperty(CropBlock.AGE)) {
                    int growth = blockState.getValue(CropBlock.AGE);
                    event.player.level.setBlockAndUpdate(growablePos, blockState.setValue(CropBlock.AGE, growth < 7 ? growth + 1 : 7));
                } else if (blockState.getBlock() instanceof BonemealableBlock) {
                    BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), event.player.level, growablePos, event.player);
                }
                ((ServerLevel)event.player.level).sendParticles((ServerPlayer) event.player, ParticleTypes.HAPPY_VILLAGER, false, growablePos.getX() + event.player.level.random.nextDouble(), growablePos.getY() + event.player.level.random.nextDouble(), growablePos.getZ() + event.player.level.random.nextDouble(), 10, 0, 0, 0, 3);
            }
        }

        private List<BlockPos> getNearestBlocks(Level level, BlockPos pos) {
            List<BlockPos> list = new ArrayList<>();
            for (int x = -TwerkConfig.effectRadius; x <= TwerkConfig.effectRadius; x++)
                for (int y = -2; y <= 2; y++)
                    for (int z = -TwerkConfig.effectRadius; z <= TwerkConfig.effectRadius; z++) {
                        Block block = level.getBlockState(new BlockPos(x + pos.getX(), y + pos.getY(), z + pos.getZ())).getBlock();
                        if (block instanceof BonemealableBlock) {
                            if (FilterListHelper.shouldAllow(block.getRegistryName().toString())) {
                                list.add(new BlockPos(x + pos.getX(), y + pos.getY(), z + pos.getZ()));
                            }
                        }
                    }
            return list;
        }

        private CompoundTag createCompoundTag(BlockPos pos) {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("x", pos.getX());
            nbt.putInt("y", pos.getY());
            nbt.putInt("z", pos.getZ());
            return nbt;
        }
    }
}
