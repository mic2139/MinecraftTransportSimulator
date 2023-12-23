package minecrafttransportsimulator.entities.instances;

import java.util.UUID;

import minecrafttransportsimulator.baseclasses.RotationMatrix;
import minecrafttransportsimulator.entities.components.AEntityE_Interactable;
import minecrafttransportsimulator.entities.components.AEntityF_Multipart;
import minecrafttransportsimulator.entities.instances.PartGun.GunState;
import minecrafttransportsimulator.items.components.AItemBase;
import minecrafttransportsimulator.items.components.AItemPack;
import minecrafttransportsimulator.items.instances.ItemPartGun;
import minecrafttransportsimulator.jsondefs.JSONCameraObject;
import minecrafttransportsimulator.jsondefs.JSONDummyPartProvider;
import minecrafttransportsimulator.jsondefs.JSONPartDefinition;
import minecrafttransportsimulator.mcinterface.AWrapperWorld;
import minecrafttransportsimulator.mcinterface.IWrapperItemStack;
import minecrafttransportsimulator.mcinterface.IWrapperNBT;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packloading.PackParser;
import minecrafttransportsimulator.systems.ConfigSystem;

/**
 * Entity class responsible for storing and syncing information about the current gun
 * any player is holding.  This entity will trigger rendering of the held gun, if it exists.
 * The current item the player is holding is stored, and whenever the player either changes
 * this item, or stops firing, the data is saved back to that item to ensure that the gun's
 * state is maintained.
 *
 * @author don_bruce
 */
public class EntityPlayerGun extends AEntityF_Multipart<JSONDummyPartProvider> {
    public static EntityPlayerGun playerClientGun;

    public final UUID playerID;
    public final IWrapperPlayer player;
    private final RotationMatrix handRotation = new RotationMatrix();
    private int hotbarSelected = -1;
    private IWrapperItemStack gunStack;
    private boolean didGunFireLastTick;
    public PartGun activeGun;

    public EntityPlayerGun(AWrapperWorld world, IWrapperPlayer placingPlayer, IWrapperNBT data) {
        super(world, placingPlayer, data);
        //Get the player spawning us.
        if (placingPlayer != null) {
            //Newly-spawned entity.
            this.playerID = placingPlayer.getID();
            this.player = placingPlayer;
            position.set(player.getPosition());
            prevPosition.set(position);
        } else if (!world.isClient()) {
            //Guns on servers without placing players are invalid and will be removed.
            //Only spawn fresh ones, since we need to do this whenever the player dies or changes dims.
            this.playerID = data.getUUID("playerUUID");
            this.player = null;
        } else {
            //Saved entity sent from server to client.
            //Get player via saved NBT.  If the player isn't found, we need to just wait.
            //Server guns without players will kill themselves and will take us with them if they're invalid.
            this.playerID = data.getUUID("playerUUID");
            this.player = (IWrapperPlayer) world.getExternalEntity(playerID);
        }

        //If we are the gun for the current client player, set us as such.
        //This is a special gun since it can control things on our client.
        if (world.isClient() && InterfaceManager.clientInterface.getClientPlayer().equals(player)) {
            playerClientGun = this;
        }
    }

    @Override
    public JSONDummyPartProvider generateDefaultDefinition() {
        JSONDummyPartProvider defaultDefinition = JSONDummyPartProvider.generateDummy();

        //Look though all gun types and add them.
        JSONPartDefinition fakeDef = defaultDefinition.parts.get(0);
        for (AItemPack<?> packItem : PackParser.getAllPackItems()) {
            if (packItem instanceof ItemPartGun) {
                ItemPartGun gunItem = (ItemPartGun) packItem;
                if (gunItem.definition.gun.handHeld) {
                    if (!fakeDef.types.contains(gunItem.definition.generic.type)) {
                        fakeDef.types.add(gunItem.definition.generic.type);
                    }
                }
            }
        }

        return defaultDefinition;
    }

    @Override
    public void update(EntityUpdateAction updateAction) {
        super.update(updateAction);
        //Make sure player is still valid and haven't left the server or the world we are in.
        if (player != null && player.isValid() && world == player.getWorld()) {
            //Set our position to the player's position.  We may update this later if we have a gun.
            //We can't update position without the gun as it has an offset defined in it.
            position.set(player.getPosition());
            motion.set(player.getVelocity());

            //Get the current gun.
            activeGun = parts.isEmpty() ? null : (PartGun) parts.get(0);

            //If we have a gun, but the player's held stack is null, get it now.
            //This happens if we load a gun as a saved part.
            if (activeGun != null && gunStack == null) {
                AItemBase heldItem = player.getHeldItem();
                if (heldItem instanceof ItemPartGun) {
                    ItemPartGun heldGun = (ItemPartGun) heldItem;
                    if (heldGun.definition.gun.handHeld) {
                        gunStack = player.getHeldStack();
                        hotbarSelected = player.getHotbarIndex();
                    }
                }
                if (activeGun != null && gunStack == null) {
                    //Either the player's held item changed, or the pack did.
                    //Held gun is invalid, so don't use or save it.
                    removePart(activeGun, true, null);
                    activeGun = null;
                }
            }

            if (!world.isClient()) {
                //Check to make sure if we had a gun, that it didn't change.
                AItemBase heldItem = player.getHeldItem();
                ItemPartGun heldGun = heldItem instanceof ItemPartGun ? (ItemPartGun) heldItem : null;
                if (activeGun != null && (heldGun == null || activeGun.definition != heldGun.definition || hotbarSelected != player.getHotbarIndex())) {
                    saveGun(true);
                }

                //If we don't have a gun yet, try to get the current one if the player is holding one.
                if (activeGun == null && heldGun != null) {
                    if (heldGun.definition.gun.handHeld) {
                        gunStack = player.getHeldStack();
                        addPartFromStack(gunStack, player, 0, true);
                        hotbarSelected = player.getHotbarIndex();
                    }
                }
            }

            //If we have a gun, do updates to it.
            //Only change firing command on servers to prevent de-syncs.
            //Packets will get sent to clients to change them.
            if (activeGun != null) {
                AEntityE_Interactable<?> ridingEntity = player.getEntityRiding();
                RotationMatrix playerRotation = ridingEntity != null ? ridingEntity.riderRelativeOrientation : player.getOrientation();

                //Offset to the end of the hand with our offset and current rotation.
                if (activeGun.isHandHeldGunAimed) {
                    position.set(activeGun.definition.gun.handHeldAimedOffset);
                } else {
                    position.set(activeGun.definition.gun.handHeldNormalOffset);
                }

                //Add model offset if present.
                if (activeGun.definition.gun.handHeldModelOffset != null) {
                    position.add(activeGun.definition.gun.handHeldModelOffset);
                }

                //If we are left-handed, offset the position in the -X direction to mirror.
                if (!player.isRightHanded()) {
                    position.x = -position.x;
                }

                //Adjust position by pitch, this only affects the arm position.
                handRotation.setToZero().rotateX(playerRotation.angles.x);
                position.rotate(handRotation);

                //Adjust position to be centered at the center of the arm.
                //Arm center is 0.375 blocks down in Y, 0.3125 blocks away in X.
                position.add(player.isRightHanded() ? -0.3125 : 0.3125, -0.375, 0);

                //Now account for yaw since we have our offset.
                handRotation.setToZero().rotateY(playerRotation.angles.y);
                position.rotate(handRotation);

                //Account for player scaling now that we have our final vector.
                position.scale(player.getVerticalScale());

                //While riding an entity, we will need to orient ourselves to the player's relative orientation, not global.
                if (ridingEntity != null) {
                    position.rotate(ridingEntity.orientation);
                    orientation.set(ridingEntity.orientation).multiply(playerRotation);
                } else {
                    orientation.set(playerRotation);
                }
                position.add(player.getHeadPosition());

                if (!world.isClient()) {
                    //Save gun data if we stopped firing the prior tick.
                    if (activeGun.state.isAtLeast(GunState.FIRING_CURRENTLY)) {
                        didGunFireLastTick = true;
                    } else if (didGunFireLastTick) {
                        saveGun(false);
                    }
                }

                //Set/unset camera index if we need to.
                if (activeGun.isHandHeldGunAimed || activeGun.definition.gun.forceHandheldCameras) {
                    if (cameraIndex == 0 && cameras.size() > 0) {
                        ++cameraIndex;
                    }
                } else if (cameraIndex != 0) {
                    cameraIndex = 0;
                    activeCamera = null;
                }
            }
        } else {
            //Player is either null or not valid.  Remove us if we are on a server.
            //Don't update post movement, as the gun will crash on update.
            if (!world.isClient()) {
                remove();
            }
            return;
        }

        //If we have a gun, and the player is spectating, don't allow the gun to render.
        if (activeGun != null) {
            activeGun.isInvisible = player != null && player.isSpectator();
        }
    }

    @Override
    public EntityUpdateAction getUpdateAction() {
        return world.isChunkLoaded(position) ? EntityUpdateAction.ALL : EntityUpdateAction.NONE;
    }

    @Override
    public EntityUpdateType getUpdateType() {
        //Player guns are queued to be ticked after their players are updated.
        //If not, then they don't move to the right spot.
        return EntityUpdateType.LAST;
    }

    @Override
    public void updatePartList() {
        super.updatePartList();

        //Populate camera list.
        for (APart part : allParts) {
            if (part.definition.rendering != null && part.definition.rendering.cameraObjects != null) {
                for (JSONCameraObject camera : part.definition.rendering.cameraObjects) {
                    cameras.add(camera);
                    cameraEntities.put(camera, part);
                }
            }
        }
    }

    @Override
    public boolean shouldSendDataToClients() {
        return true;
    }

    @Override
    public boolean requiresDeltaUpdates() {
        return true;
    }

    @Override
    public void remove() {
        super.remove();
        if (playerClientGun == this) {
            playerClientGun = null;
        }
    }

    @Override
    public boolean shouldRenderBeams() {
        return ConfigSystem.client.renderingSettings.vehicleBeams.value;
    }

    @Override
    protected void updateCollisionBoxes() {
        //Do nothing and don't add any collision.  This could block player actions.
    }

    @Override
    protected void updateEncompassingBox() {
        //Do nothing and don't add any interaction.  This could block player actions.
    }

    private void saveGun(boolean remove) {
        gunStack.setData(activeGun.save(InterfaceManager.coreInterface.getNewNBTWrapper()));
        didGunFireLastTick = false;
        if (remove) {
            removePart(activeGun, true, null);
            activeGun = null;
        }
    }

    @Override
    public boolean disableRendering() {
        //Don't render the player gun entity.  Only render the gun itself.
        return true;
    }

    @Override
    public IWrapperNBT save(IWrapperNBT data) {
        super.save(data);
        data.setBoolean("isPlayerGun", true);
        if (player != null) {
            data.setUUID("playerUUID", player.getID());
        }
        return data;
    }
}
