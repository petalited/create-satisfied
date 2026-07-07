package com.createsatisfied.client.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.createsatisfied.CreateSatisfied;
import com.createsatisfied.client.SpeedFormula;
import com.createsatisfied.client.ThroughputFormat;
import com.createsatisfied.config.CreateSatisfiedConfig;
import com.createsatisfied.mixin.client.BasinOperatingBlockEntityAccessor;
import com.createsatisfied.mixin.client.SpoutBlockEntityAccessor;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CClient;

import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

public class GoggleThroughputOverlay {

    // Set exactly once per frame (regardless of which path onRenderGui takes) so onMouseScroll can
    // gate its hotbar-scroll-cancel on "is a real tooltip on screen right now" - if this were only
    // ever updated on the success path, it would flip true once and never back to false on the
    // many frames where you're not looking at a recognized machine, hijacking scroll everywhere.
    private static boolean visible;

    // Latches once a resolveTooltip() call throws, so a broken Mixin (e.g. a future Create update
    // renaming a field/method our accessor mixins target) degrades to "no overlay" instead of
    // spamming the log or crashing the render thread every single frame the player looks at the
    // affected machine - see the mixins.json required=false comment for the other half of this.
    private static boolean brokenCompat;

    private GoggleThroughputOverlay() {
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> tooltip = brokenCompat ? null : safeResolveTooltip(mc);

        visible = tooltip != null && tooltip.size() > 1;

        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }
        if (visible) {
            tooltip.add(ThroughputFormat.scrollHintLine());
        }

        int width = event.getGuiGraphics().guiWidth();
        int height = event.getGuiGraphics().guiHeight();
        int maxLineWidth = 0;
        for (Component line : tooltip) {
            maxLineWidth = Math.max(maxLineWidth, mc.font.width(line));
        }
        int x = width / 2 - maxLineWidth / 2;
        // guiHeight() shrinks in gui-scaled units as the player's gui scale goes up, so a fixed
        // pixel offset from center can end up overlapping the hotbar at high scales - clamp
        // against the actual height (matches vanilla's own tooltip box height formula) instead
        // of trusting a constant offset to always leave room.
        int tooltipHeight = 8 + (tooltip.size() > 1 ? 2 + (tooltip.size() - 1) * 10 : 0);
        int hotbarReserve = 45;
        // Anchored below screen center rather than near the bottom - close enough to the
        // crosshair to glance at without looking away from where you're aiming, while staying
        // clear of Create's own goggle/hover tooltip (which renders right at/near center).
        int y = Math.min(height / 2 + 90, height - tooltipHeight - hotbarReserve);

        CClient cfg = AllConfigs.client();
        boolean useCustomColor = cfg.overlayCustomColor.get();
        Color colorBackground = useCustomColor
            ? new Color(cfg.overlayBackgroundColor.get())
            : BoxElement.COLOR_VANILLA_BACKGROUND.scaleAlpha(0.75F);
        Color colorBorderTop = useCustomColor
            ? new Color(cfg.overlayBorderColorTop.get())
            : BoxElement.COLOR_VANILLA_BORDER.getFirst().copy();
        Color colorBorderBot = useCustomColor
            ? new Color(cfg.overlayBorderColorBot.get())
            : BoxElement.COLOR_VANILLA_BORDER.getSecond().copy();

        RemovedGuiUtils.drawHoveringText(
            event.getGuiGraphics(), tooltip, x, y, width, height, -1,
            colorBackground.getRGB(), colorBorderTop.getRGB(), colorBorderBot.getRGB(), mc.font
        );
    }

    /**
     * The Mixer/Press/Spout tooltip builders cast to accessor interfaces added by this mod's
     * Mixins (see {@code createsatisfied.mixins.json}, now {@code required=false} so a mismatched
     * Create version doesn't hard-crash the whole mod at boot). If a future Create update shifts
     * one of the targeted fields/methods, that Mixin silently fails to apply, and the cast throws
     * a {@link ClassCastException} the instant you look at that specific machine - every frame,
     * which without this guard would either spam the log endlessly or (since render-thread
     * exceptions are often fatal) crash the game outright. Caught once, logged once, and latched
     * via {@link #brokenCompat} so every other machine's overlay keeps working normally.
     */
    private static List<Component> safeResolveTooltip(Minecraft mc) {
        try {
            return resolveTooltip(mc);
        } catch (Throwable t) {
            brokenCompat = true;
            CreateSatisfied.LOGGER.error(
                "Create: Satisfied's goggle overlay failed and has been disabled for this session - "
                    + "likely an incompatible Create version. The rest of the mod (JEI sliders) is unaffected.",
                t
            );
            return null;
        }
    }

    /**
     * Null (not just empty) means "nothing recognized here at all" - distinct from a recognized
     * machine returning an empty list, though in practice every builder below returns a non-empty
     * list or an empty one, never null; this only ever returns null itself, from the guard clauses.
     */
    private static List<Component> resolveTooltip(Minecraft mc) {
        if (mc.options.hideGui || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return null;
        }
        if (!(mc.hitResult instanceof BlockHitResult result) || mc.player == null || mc.level == null) {
            return null;
        }
        if (!GogglesItem.isWearingGoggles(mc.player)) {
            return null;
        }

        ClientLevel level = mc.level;
        BlockEntity be = level.getBlockEntity(result.getBlockPos());
        CrushingWheelBlockEntity clickedWheel = be instanceof CrushingWheelBlockEntity wheel ? wheel : null;
        if (clickedWheel != null) {
            be = findCrushingWheelController(level, result.getBlockPos(), clickedWheel.getBlockState());
        }

        if (be instanceof MillstoneBlockEntity millstone) {
            return buildMillstoneTooltip(millstone, level);
        } else if (be instanceof CrushingWheelControllerBlockEntity crusher) {
            return buildCrusherTooltip(crusher, clickedWheel);
        } else if (be instanceof MechanicalPressBlockEntity press) {
            return buildPressTooltip(press, level);
        } else if (be instanceof MechanicalMixerBlockEntity mixer) {
            Recipe<?> currentRecipe = ((BasinOperatingBlockEntityAccessor) mixer).createsatisfied$getCurrentRecipe();
            return buildBasinMachineTooltip(mixer, currentRecipe, SpeedFormula.mixer(), "Mixing", AllBlocks.MECHANICAL_MIXER.get());
        } else if (be instanceof SawBlockEntity saw) {
            return buildSawTooltip(saw, level);
        } else if (be instanceof DeployerBlockEntity deployer) {
            return buildDeployerTooltip(deployer, level);
        } else if (be instanceof EncasedFanBlockEntity fan) {
            return buildFanTooltip(fan, level);
        } else if (be instanceof BeltBlockEntity belt) {
            return buildBeltTooltip(belt);
        } else if (be instanceof PumpBlockEntity pump) {
            return buildPumpTooltip(pump);
        } else if (be instanceof SpoutBlockEntity spout) {
            return buildSpoutTooltip(spout, level);
        } else {
            return null;
        }
    }

    /**
     * Scroll normally switches hotbar slots, so this only intercepts (and cancels, so the hotbar
     * doesn't ALSO change) while the overlay actually has a tooltip on screen.
     */
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!visible) {
            return;
        }
        double deltaY = event.getScrollDeltaY();
        if (deltaY == 0) {
            return;
        }
        CreateSatisfiedConfig.cycleTimeUnit(deltaY > 0 ? 1 : -1);
        event.setCanceled(true);
    }

    private static CrushingWheelControllerBlockEntity findCrushingWheelController(
        ClientLevel level, BlockPos wheelPos, BlockState wheelState
    ) {
        Direction.Axis axis = wheelState.getValue(BlockStateProperties.AXIS);
        for (Direction dir : Direction.values()) {
            if (dir.getAxis() == axis) {
                continue;
            }
            if (level.getBlockEntity(wheelPos.relative(dir)) instanceof CrushingWheelControllerBlockEntity controller) {
                return controller;
            }
        }
        return null;
    }

    private static List<Component> buildMillstoneTooltip(MillstoneBlockEntity millstone, ClientLevel level) {
        List<Component> tooltip = new ArrayList<>();

        ItemStack input = millstone.inputInv.getStackInSlot(0);
        if (input.isEmpty()) {
            return tooltip;
        }

        Component kineticState = ThroughputFormat.kineticStateLine(millstone);
        if (kineticState != null) {
            tooltip.add(kineticState);
            return tooltip;
        }
        float speed = millstone.getSpeed();

        Optional<RecipeHolder<MillingRecipe>> found = AllRecipeTypes.MILLING.find(new RecipeWrapper(millstone.inputInv), level);
        if (found.isEmpty()) {
            return tooltip;
        }

        MillingRecipe recipe = found.get().value();
        int processingSpeed = millstone.getProcessingSpeed();
        float ticksPerOperation = recipe.getProcessingDuration() / (float) processingSpeed;
        float opsPerMinute = ThroughputFormat.TICKS_PER_MINUTE / ticksPerOperation;

        tooltip.add(Component.literal("Milling").withStyle(ChatFormatting.WHITE));
        tooltip.addAll(ThroughputFormat.buildRateLines(recipe, opsPerMinute));
        tooltip.add(ThroughputFormat.stressLine(AllBlocks.MILLSTONE.get(), speed));

        return tooltip;
    }

    private static List<Component> buildCrusherTooltip(CrushingWheelControllerBlockEntity crusher, CrushingWheelBlockEntity clickedWheel) {
        List<Component> tooltip = new ArrayList<>();

        if (crusher.hasEntity()) {
            return tooltip;
        }

        ItemStack input = crusher.inventory.getStackInSlot(0);
        if (input.isEmpty()) {
            return tooltip;
        }

        // clickedWheel is one of the two physical wheels resolved to this shared controller - its
        // own KineticBlockEntity state (overstressed/speed) is what matters for the whole pair,
        // since both wheels sit on the same network and always report the same values.
        if (clickedWheel != null) {
            Component kineticState = ThroughputFormat.kineticStateLine(clickedWheel);
            if (kineticState != null) {
                tooltip.add(kineticState);
                return tooltip;
            }
        } else if (crusher.crushingspeed == 0) {
            tooltip.add(Component.literal("Stalled").withStyle(ChatFormatting.GRAY));
            return tooltip;
        }

        Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> found = crusher.findRecipe();
        if (found.isEmpty()) {
            return tooltip;
        }

        StandardProcessingRecipe<RecipeWrapper> recipe = found.get().value();
        int stackCount = input.getCount();
        float speed = crusher.crushingspeed * 4f;
        // log2(1) == 0 for a lone item, which would divide by zero - matches the real block
        // entity's behavior of always running at max speed for a single item.
        float processingSpeed = stackCount <= 1
            ? 20f
            : Mth.clamp(speed / (float) (Math.log(stackCount) / Math.log(2)), 0.25f, 20f);
        // The real block entity applies the recipe once its countdown drops below this
        // threshold, not below zero - see CrushingWheelControllerBlockEntity.tick().
        float ticksPerBatch = Math.max((recipe.getProcessingDuration() - 20f) / processingSpeed, 1f);
        float secondsPerBatch = ticksPerBatch / 20f;

        String header = String.format("Crushing: %s for %d item%s", ThroughputFormat.formatDuration(ticksPerBatch), stackCount, stackCount == 1 ? "" : "s");
        tooltip.add(Component.literal(header).withStyle(ChatFormatting.WHITE));
        tooltip.addAll(ThroughputFormat.buildBatchLines(recipe, stackCount, secondsPerBatch));
        if (clickedWheel != null) {
            // Both wheels contribute their own stress impact - a working pair always has two.
            tooltip.add(ThroughputFormat.stressLine(AllBlocks.CRUSHING_WHEEL.get(), clickedWheel.getSpeed(), 2));
        }

        return tooltip;
    }

    private static List<Component> buildPressTooltip(MechanicalPressBlockEntity press, ClientLevel level) {
        Recipe<?> basinRecipe = ((BasinOperatingBlockEntityAccessor) press).createsatisfied$getCurrentRecipe();
        if (basinRecipe != null) {
            return buildBasinMachineTooltip(press, basinRecipe, SpeedFormula.press(), "Packing", AllBlocks.MECHANICAL_PRESS.get());
        }

        List<Component> tooltip = new ArrayList<>();
        PressingBehaviour behaviour = press.getPressingBehaviour();
        if (behaviour.mode == PressingBehaviour.Mode.BASIN || !behaviour.running) {
            return tooltip;
        }

        Component kineticState = ThroughputFormat.kineticStateLine(press);
        if (kineticState != null) {
            tooltip.add(kineticState);
            return tooltip;
        }
        float speed = press.getSpeed();

        ItemStack input = findPressInput(press, behaviour, level);
        if (input.isEmpty()) {
            return tooltip;
        }

        if (input.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            return buildSequencedAssemblyStepTooltip(input, level);
        }

        Optional<RecipeHolder<PressingRecipe>> found = press.getRecipe(input);
        if (found.isEmpty()) {
            return tooltip;
        }

        PressingRecipe recipe = found.get().value();
        float opsPerMinute = SpeedFormula.press().opsPerMinute(speed, recipe);

        tooltip.add(Component.literal("Pressing").withStyle(ChatFormatting.WHITE));
        tooltip.addAll(ThroughputFormat.buildRateLines(recipe, opsPerMinute));
        tooltip.add(ThroughputFormat.stressLine(AllBlocks.MECHANICAL_PRESS.get(), speed));

        return tooltip;
    }

    // PressingBehaviour.particleItems only exists for the instant the client spawns hit
    // particles - spawnParticles() clears it right after, so by the time this renders on
    // RenderGuiEvent.Post it's almost always already empty. Read the actual world/belt/depot
    // state instead, which stays present for the whole running animation.
    private static ItemStack findPressInput(MechanicalPressBlockEntity press, PressingBehaviour behaviour, ClientLevel level) {
        BlockPos pos = press.getBlockPos();
        if (behaviour.mode == PressingBehaviour.Mode.BELT) {
            // The press's arm reaches down through the block it sits in plus one more to hit the
            // belt/depot two blocks below - same offset the Spout's hose uses (its render bounding
            // box literally expands 2 blocks downward for the same reason).
            return findBeltOrDepotItem(level, pos.below(2));
        }

        // WORLD mode: the item is an entity sitting on the ground directly below the press,
        // the same spot PressingBehaviour.applyInWorld() scans.
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos.below()))) {
            if (itemEntity.isAlive()) {
                return itemEntity.getItem();
            }
        }
        return ItemStack.EMPTY;
    }

    // Depots expose the same TransportedItemStackHandlerBehaviour a belt does, so machines
    // reaching down onto a depot also see it this way instead of as a BELT-mode special case.
    private static ItemStack findBeltOrDepotItem(ClientLevel level, BlockPos pos) {
        BlockEntity at = level.getBlockEntity(pos);
        if (at instanceof BeltBlockEntity belt) {
            for (TransportedItemStack transported : belt.getInventory().getTransportedItems()) {
                if (!transported.stack.isEmpty()) {
                    return transported.stack;
                }
            }
        } else if (at instanceof DepotBlockEntity depot) {
            return depot.getHeldItem();
        }
        return ItemStack.EMPTY;
    }

    /**
     * Machines processing a Sequenced Assembly step are actually running a dummy recipe whose
     * input and output are both the same placeholder "in-progress" item (see
     * SequencedAssemblyRecipeBuilder.addStep) - the real transformation only exists via the
     * SEQUENCED_ASSEMBLY data component tracking (recipeId, step, progress). Showing that dummy
     * recipe's own in/out would read as "Item -> same Item", so this shows step progress instead,
     * the same info Create's own vanilla item tooltip shows via SequencedAssemblyRecipe.addToTooltip.
     */
    private static List<Component> buildSequencedAssemblyStepTooltip(ItemStack stack, ClientLevel level) {
        List<Component> tooltip = new ArrayList<>();

        SequencedAssemblyRecipe.SequencedAssembly progress = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        if (progress == null) {
            return tooltip;
        }

        Optional<RecipeHolder<?>> holder = level.getRecipeManager().byKey(progress.id());
        if (holder.isEmpty() || !(holder.get().value() instanceof SequencedAssemblyRecipe recipe)) {
            return tooltip;
        }

        int length = recipe.getSequence().size();
        int step = progress.step();
        int total = length * recipe.getLoops();
        SequencedRecipe<?> current = recipe.getSequence().get(step % length);
        Component description = current.getAsAssemblyRecipe().getDescriptionForAssembly();

        tooltip.add(Component.literal("Sequenced Assembly").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal("Step " + (step + 1) + "/" + total + " - ").append(description).withStyle(ChatFormatting.AQUA));
        return tooltip;
    }

    private static List<Component> buildSawTooltip(SawBlockEntity saw, ClientLevel level) {
        List<Component> tooltip = new ArrayList<>();

        ItemStack input = saw.inventory.getStackInSlot(0);
        if (input.isEmpty()) {
            return tooltip;
        }

        Component kineticState = ThroughputFormat.kineticStateLine(saw);
        if (kineticState != null) {
            tooltip.add(kineticState);
            return tooltip;
        }
        float speed = saw.getSpeed();

        if (input.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            return buildSequencedAssemblyStepTooltip(input, level);
        }

        Optional<RecipeHolder<CuttingRecipe>> found = AllRecipeTypes.CUTTING.find(new RecipeWrapper(saw.inventory), level);
        if (found.isEmpty()) {
            return tooltip;
        }

        CuttingRecipe recipe = found.get().value();
        float opsPerMinute = SpeedFormula.continuous(24f, 1f, 128f).opsPerMinute(speed, recipe);

        tooltip.add(Component.literal("Sawing").withStyle(ChatFormatting.WHITE));
        tooltip.addAll(ThroughputFormat.buildRateLines(recipe, opsPerMinute));
        tooltip.add(ThroughputFormat.stressLine(AllBlocks.MECHANICAL_SAW.get(), speed));

        return tooltip;
    }

    private static List<Component> buildDeployerTooltip(DeployerBlockEntity deployer, ClientLevel level) {
        List<Component> tooltip = new ArrayList<>();

        Component kineticState = ThroughputFormat.kineticStateLine(deployer);
        if (kineticState != null) {
            tooltip.add(kineticState);
            return tooltip;
        }
        float speed = deployer.getSpeed();

        // ItemApplicationRecipe (Deploying, including Sequenced Assembly steps) only ever runs
        // against a belt/depot directly below a DOWN-facing deployer - see
        // BeltDeployerCallbacks.onItemReceived/whenItemHeld. A deployer facing any other
        // direction is doing PUNCH/USE actions against blocks/entities in the world instead,
        // which aren't ProcessingRecipe-based and out of scope here.
        BlockState blockState = deployer.getBlockState();
        if (!blockState.hasProperty(DirectionalKineticBlock.FACING) || blockState.getValue(DirectionalKineticBlock.FACING) != Direction.DOWN) {
            return tooltip;
        }

        // The hand only reaches ~1.25 blocks (see DeployerBlockEntity.reach), i.e. the block
        // immediately below - not the 2-block reach Press/Spout use for their taller rigs.
        ItemStack target = findBeltOrDepotItem(level, deployer.getBlockPos().below());
        if (target.isEmpty()) {
            return tooltip;
        }

        if (target.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            return buildSequencedAssemblyStepTooltip(target, level);
        }

        // getRecipe's "stack" param is the target item (this), not the deployer's own held item -
        // it reads the held item internally via its fake player. Passing the held item here (as
        // this used to) matched both recipe ingredient slots against the same item, which only
        // ever matched degenerate recipes - see BeltDeployerCallbacks for the real call pattern.
        RecipeHolder<?> found = deployer.getRecipe(target);
        if (found == null || !(found.value() instanceof ProcessingRecipe<?, ?> recipe)) {
            return tooltip;
        }

        float opsPerMinute = SpeedFormula.deployer().opsPerMinute(speed, recipe);

        tooltip.add(Component.literal("Deploying").withStyle(ChatFormatting.WHITE));
        tooltip.addAll(ThroughputFormat.buildRateLines(recipe, opsPerMinute));
        tooltip.add(ThroughputFormat.stressLine(AllBlocks.DEPLOYER.get(), speed));

        return tooltip;
    }

    private static List<Component> buildFanTooltip(EncasedFanBlockEntity fan, ClientLevel level) {
        List<Component> tooltip = new ArrayList<>();

        Component kineticState = ThroughputFormat.kineticStateLine(fan);
        if (kineticState != null) {
            tooltip.add(kineticState);
            return tooltip;
        }
        float speed = fan.getSpeed();

        Direction flowDirection = fan.getAirFlowDirection();
        if (flowDirection == null) {
            return tooltip;
        }

        int maxDistance = (int) fan.airCurrent.maxDistance;
        FanProcessingType type = null;
        for (int i = 1; i <= maxDistance; i++) {
            FanProcessingType candidate = FanProcessingType.getAt(level, fan.getBlockPos().relative(flowDirection, i));
            if (candidate != null) {
                type = candidate;
                break;
            }
        }
        if (type == null) {
            return tooltip;
        }

        // No specific item/recipe is identified here (fan processing types don't expose "what
        // recipe would apply", only "can this item be processed"), so show the batching
        // capability itself rather than a guessed item: processing time is flat in chunks of 16.
        float ticksFor16 = ThroughputFormat.fanProcessingTicks(16);
        float ticksFor64 = ThroughputFormat.fanProcessingTicks(64);

        tooltip.add(Component.literal(fanProcessingTypeName(type)).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal("1-16 items: " + ThroughputFormat.formatDuration(ticksFor16)).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("64 items: " + ThroughputFormat.formatDuration(ticksFor64)).withStyle(ChatFormatting.GREEN));
        tooltip.add(ThroughputFormat.stressLine(AllBlocks.ENCASED_FAN.get(), speed));

        return tooltip;
    }

    private static List<Component> buildBeltTooltip(BeltBlockEntity belt) {
        List<Component> tooltip = new ArrayList<>();

        Component kineticState = ThroughputFormat.kineticStateLine(belt);
        if (kineticState != null) {
            tooltip.add(kineticState);
            return tooltip;
        }
        float speed = belt.getSpeed();

        // Belts don't process recipes - throughput is purely a transport limit. BeltInventory.tick()
        // hardcodes a minimum spacing of 1.0 (in belt-position units, i.e. blocks) between item
        // stacks, and getBeltMovementSpeed() = getSpeed()/480 is how many of those units an item
        // advances per tick - so max stacks/tick == that movement speed directly (spacing 1:1).
        float stacksPerMinute = Math.abs(speed) / 480f * ThroughputFormat.TICKS_PER_MINUTE;

        tooltip.add(Component.literal("Belt").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal("Single items: " + ThroughputFormat.formatItemRate(stacksPerMinute)).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("Full stacks (64): " + ThroughputFormat.formatItemRate(stacksPerMinute * 64)).withStyle(ChatFormatting.GREEN));
        // Only the controller segment of a multi-block belt actually carries stress impact -
        // BeltBlockEntity.calculateStressApplied() returns 0 for the rest - so showing this on a
        // non-controller segment would claim a cost that isn't real.
        if (belt.isController()) {
            tooltip.add(ThroughputFormat.stressLine(AllBlocks.BELT.get(), speed));
        }

        return tooltip;
    }

    private static List<Component> buildPumpTooltip(PumpBlockEntity pump) {
        List<Component> tooltip = new ArrayList<>();

        Component kineticState = ThroughputFormat.kineticStateLine(pump);
        if (kineticState != null) {
            tooltip.add(kineticState);
            return tooltip;
        }
        float speed = pump.getSpeed();

        // Pumps don't process recipes either - fluid transfer speed is a network-wide "pressure"
        // system, not a per-pump rate, but FluidNetwork always reads a fresh transferSpeed off the
        // pump at the start of its queue, and that pump's own contributed pressure is exactly
        // abs(rpm) (see PumpFluidTransferBehaviour.tick()). FluidNetwork.transferSpeed then does
        // max(1, pressure/2) mB per tick - matching that exactly here.
        float mbPerTick = Math.max(1f, Math.abs(speed) / 2f);
        float mbPerMinute = mbPerTick * ThroughputFormat.TICKS_PER_MINUTE;

        tooltip.add(Component.literal("Pump").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal(ThroughputFormat.formatFluidRate(mbPerMinute)).withStyle(ChatFormatting.GREEN));
        tooltip.add(ThroughputFormat.stressLine(AllBlocks.MECHANICAL_PUMP.get(), speed));

        return tooltip;
    }

    private static List<Component> buildSpoutTooltip(SpoutBlockEntity spout, ClientLevel level) {
        List<Component> tooltip = new ArrayList<>();

        SmartFluidTankBehaviour tank = ((SpoutBlockEntityAccessor) spout).createsatisfied$getTank();
        if (tank == null || tank.isEmpty()) {
            tooltip.add(Component.literal("Empty tank").withStyle(ChatFormatting.GRAY));
            return tooltip;
        }

        // Same 2-block-down reach as the press's BELT mode - the spout's render bounding box
        // literally expands 2 blocks downward to reach the belt/depot its hose fills into.
        ItemStack input = findBeltOrDepotItem(level, spout.getBlockPos().below(2));
        if (input.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            return buildSequencedAssemblyStepTooltip(input, level);
        }

        // Fill time is a flat constant (SpoutBlockEntity.FILLING_TIME) regardless of belt speed -
        // the belt only affects how long an item waits before/after being filled, not the fill
        // itself - so there's no rpm/stack-size axis to vary here, just the flat rate.
        float opsPerMinute = ThroughputFormat.TICKS_PER_MINUTE / (float) SpoutBlockEntity.FILLING_TIME;

        tooltip.add(Component.literal("Filling").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal("~" + ThroughputFormat.formatItemRate(opsPerMinute)).withStyle(ChatFormatting.GREEN));

        return tooltip;
    }

    private static String fanProcessingTypeName(FanProcessingType type) {
        if (type == AllFanProcessingTypes.SMOKING) {
            return "Smoking";
        }
        if (type == AllFanProcessingTypes.BLASTING) {
            return "Blasting";
        }
        if (type == AllFanProcessingTypes.SPLASHING) {
            return "Washing";
        }
        if (type == AllFanProcessingTypes.HAUNTING) {
            return "Haunting";
        }
        return "Processing";
    }

    private static List<Component> buildBasinMachineTooltip(
        BasinOperatingBlockEntity be, Recipe<?> currentRecipe, SpeedFormula formula, String title, Block stressBlock
    ) {
        List<Component> tooltip = new ArrayList<>();

        Component kineticState = ThroughputFormat.kineticStateLine(be);
        if (kineticState != null) {
            tooltip.add(kineticState);
            return tooltip;
        }
        float speed = be.getSpeed();

        if (!(currentRecipe instanceof ProcessingRecipe<?, ?> recipe)) {
            return tooltip;
        }

        float opsPerMinute = formula.opsPerMinute(speed, recipe);

        tooltip.add(Component.literal(title).withStyle(ChatFormatting.WHITE));
        tooltip.addAll(ThroughputFormat.buildRateLines(recipe, opsPerMinute));
        tooltip.add(ThroughputFormat.stressLine(stressBlock, speed));

        return tooltip;
    }
}
