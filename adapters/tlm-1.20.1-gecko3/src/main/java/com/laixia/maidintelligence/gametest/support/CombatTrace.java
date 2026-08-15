package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.TargetSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import net.minecraft.world.phys.Vec3;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatProfile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * What she actually did, tick by tick, printed whether or not the test passes.
 *
 * <p>A scenario that only reports pass or fail is a slower way of not knowing.
 * Every defect this harness exists to catch was live in the game while the
 * suite was green, and the thing that finally identified the worst of them was
 * not an assertion but a column of numbers. So the trace is the product here
 * and the assertion is a convenience: a passing run still prints everything,
 * because "it passed" and "it did the right thing for the right reason" are
 * different claims.
 *
 * <p>There used to be a lease-holder column, and for a while it was the most
 * important one: a maid could decide to retreat, write the target, and be
 * overruled by a host behaviour holding a stronger claim, which from outside
 * is indistinguishable from deciding to stand still. Free mode no longer
 * registers anything that can overrule her, so the column went with the arbiter
 * it reported on. {@code escape} and {@code bear} are what to read now — they
 * separate "no retreat was produced" from "one was produced and it was two feet
 * long".
 */
public final class CombatTrace {
    /**
     * How often a row is emitted even when nothing has changed.
     *
     * <p>Rows are driven by change, not by the clock. A fixed sampling interval
     * is what made the earlier traces unable to answer "why": it showed a state
     * every five ticks and silently dropped every transition in between, so a
     * decision, the write it produced and the thing that undid it could all
     * happen inside one invisible gap. The heartbeat only exists so a long
     * stretch of genuinely nothing still shows the clock moving.
     */
    private static final int HEARTBEAT_TICKS = 20;

    /** The speed modifier combat walks at, mirrored from {@code TlmCombatAction}. */
    private static final float COMBAT_SPEED = 0.6F;

    /** About how far an ordinary walking hostile can strike from. */
    private static final double MELEE_REACH = 2.5D;

    private static final TlmThreatScanner SCANNER = new TlmThreatScanner();

    private static final TlmWeaponScanner WEAPONS =
            new TlmWeaponScanner(RangedWeaponRecognizer.NONE);

    private final String name;
    private final List<String> rows = new ArrayList<>();
    private final CombatMetrics metrics = new CombatMetrics();

    /**
     * 全部敌人，不只是最近那一只。
     *
     * <p>四打一里"她为什么死"是个**阵型**问题：被夹在两只之间、退路那一侧站着
     * 第三只、盾朝着甲而挨的是乙——这些在只跟最近一只的行里全都长成同一个样子。
     */
    private List<? extends LivingEntity> watched = List.of();

    private float previousHealth = Float.NaN;

    private String previousIntent = "";
    private String previousVerdict = "";
    private String previousHeld = "";
    private boolean previousHadWalk;
    private boolean previousCooling;
    private int previousDrawn;
    private net.minecraft.world.phys.Vec3 previousPosition;
    private long lastRow = Long.MIN_VALUE;
    private double movedSinceRow;

    public CombatTrace(String name) {
        this.name = name;
        rows.add(String.join("\t",
                "tick", "ev", "pos", "mv", "nav", "goal",
                "hp", "dist", "closing", "toC",
                "intent", "verdict", "stance", "alert",
                "hand", "wpn", "escape", "bear", "canOpen", "spd",
                "foes", "conv", "inDps", "heavy", "goalToFoe",
                "hurt", "ring", "walkTo", "see", "foeAt",
                "usable", "quiver", "orch"));
    }

    /**
     * 把整队敌人交给它，`ring` 那一列才有东西可印。
     *
     * <p>做成 setter 而不是加参数：既有六个调用点一个都不用改，而只有真正需要
     * 阵型的那一局（四个卫道士）才付这份开销。
     */
    public void watch(List<? extends LivingEntity> band) {
        this.watched = band;
    }

    /**
     * Record this tick.
     *
     * <p>Sampling rather than recording every tick: the interesting quantities
     * change on the scale of a swing, not of a tick, and a two-hundred-tick
     * scenario that prints two hundred rows stops being read.
     */
    public void sample(
            long tick,
            EntityMaid maid,
            LivingEntity foe
    ) {
        double distance = maid.distanceTo(foe);
        String intent = CombatProbe.intent(maid);
        WalkTarget walk = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        metrics.record(
                maid,
                foe,
                intent,
                walk != null,
                CombatProbe.scan(maid).size(),
                CombatMetrics.moved(previousPosition, maid.position())
        );

        // Everything below is about producing a readable causal chain rather
        // than a periodic dump. Each of these is a thing that can change
        // between two ticks and change what happens next, so each of them is
        // worth a line the moment it moves.
        String verdict = CombatProbe.verdict(
                maid,
                CombatProbe.field(maid, CombatProbe.scan(maid)),
                CombatProbe.canOpenGround(maid, foe)
        );
        String held = itemOf(maid);
        String walkId = walk == null
                ? "-"
                : fixed(walk.getTarget().currentPosition()
                        .distanceTo(foe.position()));
        boolean cooling = maid.getBrain()
                .hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN);
        int drawn = maid.getTicksUsingItem();
        // Accumulated between rows, not per tick. Rows are irregularly spaced,
        // so a per-tick figure reads as a distance travelled over the gap and
        // is off by whatever the gap happens to be — I misread it that way
        // myself and chased the wrong cause for it.
        if (previousPosition != null) {
            movedSinceRow += maid.position().distanceTo(previousPosition);
        }
        double moved = movedSinceRow;

        String event = eventFor(
                intent, verdict, held, walk != null, cooling, drawn
        );

        previousIntent = intent;
        previousVerdict = verdict;
        previousHeld = held;
        previousHadWalk = walk != null;
        previousCooling = cooling;
        previousDrawn = drawn;
        previousPosition = maid.position();
        lastRow = tick;
        movedSinceRow = 0.0D;
        List<ScannedThreat> scanned = CombatProbe.scan(maid);
        List<Vec3> mob = CombatProbe.crowd(scanned);
        ThreatField crowd = CombatProbe.field(maid, scanned);
        boolean canOpen = CombatProbe.canOpenGround(maid, foe);
        ThreatSample aimedAt = scanned.isEmpty()
                ? null
                : TargetSelectionPolicy.INSTANCE.select(
                        TlmThreatScanner.samplesOf(scanned)
                );
        rows.add(String.join("\t",
                Long.toString(tick),
                event.isEmpty() ? "." : event,
                String.format("%.1f,%.1f", maid.getX(), maid.getZ()),
                // What she actually did with her feet, not what she was told to
                // do with them. "Told to retreat" and "retreated" are different
                // claims and the gap between them is where the bugs live.
                fixed(moved),
                CombatProbe.navigation(maid),
                CombatProbe.goal(maid),
                fixed(maid.getHealth()),
                fixed(distance),
                fixed(ThreatProfile.closingSpeed(maid, foe)),
                fixed(ThreatProfile.secondsToContact(maid, foe)),
                intent,
                verdict,
                CombatProbe.stance(maid, aimedAt, crowd, canOpen),
                CombatProbe.alertness(maid),
                held,
                weaponStateOf(maid, cooling, drawn),
                // What a retreat would actually buy her right now. Separates
                // "no escape produced" from "escape produced and undone" from
                // "escape produced two feet away" — three different bugs that
                // look identical from outside.
                fixed(CombatProbe.escapeReach(maid, mob)),
                // Which way that escape points, and how clear it is. The escape
                // column alone reports the same figure for "the only gap is
                // narrow" and "the gap is wide and something else stopped her".
                CombatProbe.bearing(maid, mob),
                canOpen ? "y" : "NO",
                // Per-tick arbiter outcome, not a running total. "The write was
                // rejected" and "no write was attempted" both look like an empty
                // walk target, and only this tells them apart.
                // The two raw numbers behind canOpen. "She cannot outrun it" is
                // a claim about these and nothing else, so print them rather
                // than infer them.
                String.format("%.2f/%.2f",
                        maid.getAttributeValue(
                                net.minecraft.world.entity.ai.attributes
                                        .Attributes.MOVEMENT_SPEED) * 0.6D,
                        foe.getAttributeValue(
                                net.minecraft.world.entity.ai.attributes
                                        .Attributes.MOVEMENT_SPEED)),
                Integer.toString(crowd.total()),
                Integer.toString(crowd.converging()),
                fixed(crowd.incomingDps()),
                fixed(crowd.heaviestBlow()),
                walkId,
                bloodSinceLastRow(maid),
                ring(maid),
                walkTo(walk),
                // 看不看得见，单独一列。远程站位有一支写着"看不见就把要保持的距离
                // 设成零，走过去"——那条对着墙后的一只是对的，对着地形挡住的四把
                // 斧头就是自己走进去。而只看 walkTo 分不出"她在追"和"她在退"，
                // 两者都只是一个坐标。
                maid.hasLineOfSight(foe) ? "y" : "NO",
                // 目标此刻在哪。和 walkTo 摆在一起，一眼看得出她要去的是不是它。
                String.format("%.1f,%.1f", foe.getX(), foe.getZ()),
                // 手里那件此刻算不算"能用"。`canStrike` 由它决定，而 `canStrike`
                // 又决定 `shooting` 是问手还是问姿态——远程站位距离与近战贴近距离
                // 的分岔就在这里。手持一列只说她拿着什么，说不出这一件是不是废的。
                WEAPONS.isUsable(maid, maid.getMainHandItem()) ? "y" : "NO",
                // 箭还剩几支。弓没箭就不算武器，这是"能不能用"最常见的那个原因。
                Integer.toString(quiver(maid)),
                // 编排器给这一架打了多少分、为什么没选它。intent 那一列只会在她
                // 没打的时候印一个"-"，而"没被选中"和"选中了做不到"要改的地方
                // 完全不同。
                CombatProbe.engagement(maid)
        ));
        previousHealth = maid.getHealth();
    }

    /**
     * 这一段掉了多少血，以及来自哪个方位。
     *
     * <p>整场仗只报一个总数说不出她是怎么死的：连着挨同一侧三下（盾没转过去）、
     * 和四个方向各挨一下（被围了），总数一模一样而要改的地方完全不同。
     *
     * <p>只报净损失。回血和伤害吸收会把差额抹平，那正是"苹果吃下去了没有"该由
     * 另一列回答的问题，混进来只会让这一列既不是伤害也不是净变化。
     */
    private String bloodSinceLastRow(EntityMaid maid) {
        float now = maid.getHealth();
        if (Float.isNaN(previousHealth) || now >= previousHealth) {
            return ".";
        }
        String from = maid.getLastHurtByMob() == null
                ? "?"
                : Integer.toString(sectorOf(maid, maid.getLastHurtByMob()));
        return String.format("-%.0f@%s", previousHealth - now, from);
    }

    /**
     * 周围每一只在她的哪个方位、多远。
     *
     * <p>方位相对**身体朝向**而不是世界坐标，因为格挡判的就是身体朝向
     * （{@code isDamageSourceBlocked} 拿 {@code getViewVector} 点积，而它读
     * {@code getYRot}）。所以 `0:` 那一只是盾罩得住的，`5:` 和 `6:` 那两只不是
     * ——这一列因此同时回答"被围了吗"和"盾朝对了吗"。
     *
     * <p>30° 一格，与方位场的十二扇区同一套刻度，读数可以直接对上。
     */
    private String ring(EntityMaid maid) {
        if (watched.isEmpty()) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        for (LivingEntity foe : watched) {
            if (out.length() > 0) {
                out.append(' ');
            }
            if (!foe.isAlive()) {
                out.append('x');
                continue;
            }
            out.append(sectorOf(maid, foe))
                    .append(':')
                    .append(String.format("%.1f", maid.distanceTo(foe)));
        }
        return out.toString();
    }

    /**
     * 相对她身体朝向的方位，30° 一格：0 正前，±1..±5 两侧，6 正后。
     */
    private static int sectorOf(EntityMaid maid, LivingEntity foe) {
        double dx = foe.getX() - maid.getX();
        double dz = foe.getZ() - maid.getZ();
        double toward = Math.toDegrees(Math.atan2(dz, dx)) - 90.0D;
        double relative = net.minecraft.util.Mth.wrapDegrees(
                toward - maid.getYRot()
        );
        int sector = (int) Math.round(relative / 30.0D);
        return sector == -6 ? 6 : sector;
    }

    /** 她要走去哪。和 ring 摆在一起才看得出她是不是正走进某一只怀里。 */
    private static String walkTo(WalkTarget walk) {
        if (walk == null) {
            return "-";
        }
        Vec3 to = walk.getTarget().currentPosition();
        return String.format("%.1f,%.1f", to.x, to.z);
    }

    /** Closest the threat ever got, at any point in the scenario. */
    /** Delegated to the metrics, which is what assertions are written against. */
    public double closestApproach() {
        return metrics.closestApproach();
    }

    public double closestWhileEngaged() {
        return metrics.closestWhileEngaged();
    }

    public double farthestReached() {
        return metrics.farthestReached();
    }

    public int engagedTicks() {
        return metrics.engagedTicks();
    }

    public double shareWithinReach() {
        return metrics.shareWithinReach();
    }

    public double stationaryShare() {
        return metrics.stationaryShare();
    }

    public double stationaryInReachShare() {
        return metrics.stationaryInReachShare();
    }

    public int rootedTicks() {
        return metrics.rootedTicks();
    }

    public int samples() {
        return metrics.samples();
    }

    public float damageTaken() {
        return metrics.damageTaken();
    }


    public String summary() {
        return metrics.summary();
    }

    public void dump() {
        StringBuilder out = new StringBuilder();
        out.append("\n=== combat trace: ").append(name).append(" ===\n");
        for (String row : rows) {
            out.append(row).append('\n');
        }
        out.append(summary()).append('\n');
        System.out.println(out);
    }

    /**
     * What changed this tick, as a short tag, or empty when nothing did.
     *
     * <p>Several can be true at once; the most explanatory one wins, because a
     * line that says everything says nothing.
     */
    private String eventFor(
            String intent,
            String verdict,
            String held,
            boolean hasWalk,
            boolean cooling,
            int drawn
    ) {
        if (!intent.equals(previousIntent)) {
            return "INTENT";
        }
        if (!verdict.equals(previousVerdict)) {
            return "VERDICT";
        }
        if (!held.equals(previousHeld)) {
            return "SWAP";
        }
        // A draw that shrinks was either loosed or thrown away, and which of
        // those it was is the single most repeated bug in this area.
        if (drawn < previousDrawn && previousDrawn > 0) {
            return drawn == 0 ? "RELEASE" : "DRAW-CUT";
        }
        if (cooling && !previousCooling) {
            return "SWING";
        }
        if (hasWalk != previousHadWalk) {
            return hasWalk ? "GO" : "STOP";
        }
        return "";
    }

    /** Her weapon as a state rather than as an item name. */
    private static String weaponStateOf(
            EntityMaid maid,
            boolean cooling,
            int drawn
    ) {
        if (maid.isUsingItem()) {
            return "draw:" + drawn;
        }
        return cooling ? "cooldown" : "ready";
    }


    /**
     * 两只手，一列。
     *
     * <p>只印主手会让整类缺陷隐形：副手举着盾会让 {@code isUsingItem} 为真，
     * 而换手、进食、拉弓全都以那个谓词为门。实测中"姿态已经改判近战、手里
     * 还是弓"持续到她死，就是这么来的——而只看主手那一列时，它看起来像是
     * 选择错了，不像是换手被卡住。
     *
     * <p>格式 {@code 主手+副手}，副手为空时省略，好让既有读数保持原样。
     */
    private static String itemOf(EntityMaid maid) {
        String main = maid.getMainHandItem().isEmpty()
                ? "empty"
                : maid.getMainHandItem().getItem().toString();
        if (maid.getOffhandItem().isEmpty()) {
            return main;
        }
        String off = maid.getOffhandItem().getItem().toString();
        return main + "+" + off
                + (maid.isUsingItem()
                        && maid.getUsedItemHand()
                                == net.minecraft.world.InteractionHand.OFF_HAND
                        ? "^" : "");
    }

    /** 背包加两只手里一共还有几支箭。 */
    private static int quiver(EntityMaid maid) {
        int found = 0;
        net.minecraftforge.items.IItemHandler pack =
                maid.getAvailableBackpackInv();
        for (int slot = 0; slot < pack.getSlots(); slot++) {
            net.minecraft.world.item.ItemStack stack =
                    pack.getStackInSlot(slot);
            if (stack.getItem() instanceof net.minecraft.world.item.ArrowItem) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private static String fixed(double value) {
        if (!Double.isFinite(value)) {
            return "inf";
        }
        return String.format("%.1f", value);
    }

    /**
     * Claims attempted and suppressed since the previous row.
     *
     * <p>Per tick, not a running total. "The write was rejected" and "no write
     * was attempted" both present as an empty walk target, and every wrong
     * diagnosis in this area came from being unable to tell those apart.
     */
}
