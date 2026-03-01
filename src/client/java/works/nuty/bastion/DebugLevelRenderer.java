package works.nuty.bastion;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebugLevelRenderer implements LevelRenderEvents.EndMain {
    public static final int BREAKPOINT_BLOCK_FILL_COLOR = ARGB.color(1/2f, 0xFF0000);
    public static final int BREAKPOINT_BLOCK_FILL_COLOR_ACTIVE = ARGB.color(1/2f, 0x00FFFF);
    public static final int BREAKPOINT_BLOCK_STROKE_COLOR = ARGB.color(1, 0xFF0000);

    @Override
    public void endMain(@NonNull LevelRenderContext context) {
        final DistinctColorGenerator colorGenerator = new DistinctColorGenerator();
        BlockPos pausedPos = null;

        if (Bastion.paused) {
            Bastion.InitialSource initialSource = Bastion.callstack.peek().initialSource();
            if (initialSource instanceof Bastion.InitialSource.Block(BlockPos blockPos)) {
                pausedPos = blockPos;
                Gizmos.cuboid(blockPos, 1 / 128f, GizmoStyle.strokeAndFill(BREAKPOINT_BLOCK_STROKE_COLOR, 1, BREAKPOINT_BLOCK_FILL_COLOR_ACTIVE));
            }
        }

        for (BlockPos pos : Bastion.blockBreakpoints) {
            if (pos.equals(pausedPos)) continue;
            Gizmos.cuboid(pos, 1/128f, GizmoStyle.strokeAndFill(BREAKPOINT_BLOCK_STROKE_COLOR, 1, BREAKPOINT_BLOCK_FILL_COLOR));
        }

        List<CommandSourceStack> currentPauseSources = Bastion.currentPauseSources;
        Map<Entity, Integer> entityCounter = new HashMap<>();
        Map<Pair<Vec3, Pair<Float, Float>>, Integer> arrowCounter = new HashMap<>();
        for (int i = 0; i < currentPauseSources.size(); i++) {
            CommandSourceStack source = currentPauseSources.get(i);
            final Entity entity = source.getEntity();
            final Vec3 anchoredPosition = source.getAnchor().apply(source);
            //noinspection SuspiciousNameCombination; 'y' is the correct argument for parameter 'right'
            final Pair<Vec3, Pair<Float, Float>> arrowKey = Pair.of(anchoredPosition, Pair.of(source.getRotation().x, source.getRotation().y));
            final int color = colorGenerator.nextColor();

            final int entityCnt = entityCounter.getOrDefault(entity, 0);
            final int arrowCnt = arrowCounter.getOrDefault(arrowKey, 0);

            Gizmos.point(anchoredPosition, color, 30);
            Gizmos.arrow(anchoredPosition, anchoredPosition.add(Vec3.applyLocalCoordinatesToRotation(source.getRotation(), Vec3.Z_AXIS).scale(1 + arrowCnt / 2f)), color);

            if (entity != null) {
                if (entityCnt == 0) {
                    Gizmos.billboardTextOverMob(entity, -1, entity.getStringUUID(), color, 1 / 2f);
                    Gizmos.billboardTextOverMob(entity, -2, entity.getPlainTextName(), color, 1 / 2f);
                }
                Gizmos.billboardTextOverMob(entity, entityCnt, "Source #%s".formatted(i), color, 1 / 2f);
            } else {
                Gizmos.billboardTextOverBlock("Source #%s".formatted(i), BlockPos.containing(source.getPosition()), entityCnt, color, 1 / 2f);
            }
            entityCounter.put(entity, entityCnt + 1);
            arrowCounter.put(arrowKey, arrowCnt + 1);
        }
    }
}
