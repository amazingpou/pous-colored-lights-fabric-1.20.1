package dev.prismlights;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3f;
import java.util.List;

/** Color and lifecycle are synchronized; this common class never loads client code. */
public final class PrismFireworkEntity extends ProjectileEntity implements FlyingItemEntity {
    public static final int MAX_FLIGHT_TICKS=60,BURST_TICKS=20;
    public static final double MAX_DISTANCE=40;
    private static final TrackedData<Integer> COLOR=DataTracker.registerData(PrismFireworkEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> BURST=DataTracker.registerData(PrismFireworkEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<BlockPos> BURST_BLOCK=DataTracker.registerData(PrismFireworkEntity.class,TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Vector3f> BURST_OFFSET=DataTracker.registerData(PrismFireworkEntity.class,TrackedDataHandlerRegistry.VECTOR3F);
    private int flightTicks,burstTicks;
    private double flownDistance;
    private String burstReason="";
    private ItemStack visualItem;
    private boolean burstParticlesPending;
    public PrismFireworkEntity(EntityType<? extends PrismFireworkEntity> type,World world) {
        super(type,world); setNoGravity(true);
    }
    @Override protected void initDataTracker() {
        dataTracker.startTracking(COLOR,0xFF20CC);dataTracker.startTracking(BURST,false);
        dataTracker.startTracking(BURST_BLOCK,BlockPos.ORIGIN);dataTracker.startTracking(BURST_OFFSET,new Vector3f());
    }
    public int color() { return dataTracker.get(COLOR); }
    public void setColor(int color) { dataTracker.set(COLOR,color & 0xFFFFFF); }
    public boolean bursting() { return dataTracker.get(BURST); }
    public String burstReason() { return burstReason; }
    public double flownDistance() { return flownDistance; }
    public Vec3d burstPosition() {
        BlockPos block=dataTracker.get(BURST_BLOCK);Vector3f offset=dataTracker.get(BURST_OFFSET);
        return new Vec3d((double)block.getX()+offset.x,(double)block.getY()+offset.y,(double)block.getZ()+offset.z);
    }
    public float lightRadius() { return bursting()?12:8; }
    public float lightIntensity(float tickDelta) {
        return bursting()?1.6f*(float)Math.pow(Math.max(0,1-(burstTicks+tickDelta)/BURST_TICKS),2):0.85f;
    }
    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if(data.equals(COLOR)) visualItem=null;
        if(data.equals(BURST)) {
            burstTicks=0;
            if(bursting() && getWorld().isClient) burstParticlesPending=true;
        }
    }
    @Override public ItemStack getStack() {
        if(bursting()) return ItemStack.EMPTY;
        if(visualItem==null) {
            visualItem=new ItemStack(Items.FIREWORK_STAR);
            visualItem.setSubNbt("Explosion",explosion());
        }
        return visualItem;
    }
    private NbtCompound explosion() {
        NbtCompound explosion=new NbtCompound();
        explosion.putByte("Type",(byte)0);explosion.putIntArray("Colors",new int[]{color()});
        explosion.putIntArray("FadeColors",new int[0]);explosion.putBoolean("Trail",true);explosion.putBoolean("Flicker",true);
        return explosion;
    }
    @Override public void tick() {
        super.tick();
        if(bursting()) {
            setVelocity(Vec3d.ZERO);
            if(burstParticlesPending) {
                burstParticlesPending=false;
                Vec3d point=burstPosition();
                NbtCompound fireworks=new NbtCompound();NbtList explosions=new NbtList();explosions.add(explosion());fireworks.put("Explosions",explosions);
                getWorld().addFireworkParticle(point.x,point.y,point.z,0,0,0,fireworks);
                if(Boolean.getBoolean("prism.fireworkTest")) org.slf4j.LoggerFactory.getLogger("Prism Lights")
                    .info("Firework particles id={} color={} pos={}",getId(),color(),point);
            }
            if(++burstTicks>=BURST_TICKS && !getWorld().isClient) discard();
            return;
        }
        Vec3d velocity=getVelocity();
        if(!getWorld().isClient) {
            HitResult hit=ProjectileUtil.getCollision(this,this::canHit);
            if(hit.getType()!=HitResult.Type.MISS) {
                Vec3d position=hit.getPos();
                if(hit instanceof BlockHitResult block) position=position.add(Vec3d.of(block.getSide().getVector()).multiply(0.08));
                setPosition(position);
                burst(hit.getType()==HitResult.Type.BLOCK?"block":"entity");
                return;
            }
        }
        setPosition(getPos().add(velocity));
        updateRotation();
        if(getWorld().isClient) {
            DustParticleEffect dust=trailEffect();
            for(int i=0;i<3;i++) {
                double fraction=i/3.0;
                getWorld().addParticle(dust,getX()-velocity.x*fraction,getY()-velocity.y*fraction,getZ()-velocity.z*fraction,0,0,0);
            }
        } else {
            flownDistance+=velocity.length();
            if(flownDistance>=MAX_DISTANCE) burst("distance");
            else if(++flightTicks>=MAX_FLIGHT_TICKS) burst("time");
        }
    }
    private int trailColor;
    private DustParticleEffect trail;
    private DustParticleEffect trailEffect() {
        int rgb=color();
        if(trail==null || trailColor!=rgb) {
            trailColor=rgb;
            trail=new DustParticleEffect(new Vector3f(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f),0.8f);
        }
        return trail;
    }
    private void burst(String reason) {
        if(bursting()) return;
        burstReason=reason;setVelocity(Vec3d.ZERO);
        BlockPos anchor=getBlockPos();Vec3d offset=getPos().subtract(Vec3d.of(anchor));
        dataTracker.set(BURST_BLOCK,anchor.toImmutable());
        dataTracker.set(BURST_OFFSET,new Vector3f((float)offset.x,(float)offset.y,(float)offset.z));
        // Separate authoritative burst coordinates from interpolated flight.
        // Client particles run on the next tick, after the whole metadata
        // packet has been applied; no early status/relative-movement race.
        dataTracker.set(BURST,true);
        if(Boolean.getBoolean("prism.fireworkTest")) org.slf4j.LoggerFactory.getLogger("Prism Lights")
            .info("Firework impact id={} color={} pos={} reason={}",getId(),color(),getPos(),reason);
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("PrismColor",color());nbt.putBoolean("PrismBurst",bursting());
        nbt.putInt("PrismFlightTicks",flightTicks);nbt.putInt("PrismBurstTicks",burstTicks);
        nbt.putDouble("PrismDistance",flownDistance);
        if(bursting()) {
            Vec3d point=burstPosition();nbt.putDouble("PrismBurstX",point.x);
            nbt.putDouble("PrismBurstY",point.y);nbt.putDouble("PrismBurstZ",point.z);
        }
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);setColor(nbt.getInt("PrismColor"));
        Vec3d point=nbt.contains("PrismBurstX")?new Vec3d(nbt.getDouble("PrismBurstX"),nbt.getDouble("PrismBurstY"),nbt.getDouble("PrismBurstZ")):getPos();
        BlockPos anchor=BlockPos.ofFloored(point);Vec3d offset=point.subtract(Vec3d.of(anchor));
        dataTracker.set(BURST_BLOCK,anchor);dataTracker.set(BURST_OFFSET,new Vector3f((float)offset.x,(float)offset.y,(float)offset.z));
        dataTracker.set(BURST,nbt.getBoolean("PrismBurst"));
        flightTicks=Math.max(0,nbt.getInt("PrismFlightTicks"));burstTicks=Math.max(0,nbt.getInt("PrismBurstTicks"));
        double savedDistance=nbt.getDouble("PrismDistance");
        flownDistance=Double.isFinite(savedDistance)?Math.max(0,savedDistance):0;setNoGravity(true);
    }
}
