package minecrafttransportsimulator.mcinterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import minecrafttransportsimulator.MasterLoader;
import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.blocks.components.ABlockBase;
import minecrafttransportsimulator.blocks.components.ABlockBaseTileEntity;
import minecrafttransportsimulator.blocks.components.IBlockFluidTankProvider;
import minecrafttransportsimulator.blocks.instances.BlockCollision;
import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityBase;
import minecrafttransportsimulator.blocks.tileentities.components.ITileEntityFluidTankProvider;
import minecrafttransportsimulator.items.components.AItemBase;
import minecrafttransportsimulator.items.components.AItemPack;
import minecrafttransportsimulator.items.components.IItemBlock;
import minecrafttransportsimulator.systems.PackParserSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**Builder for a basic MC Block class.  This builder assumes the block will not be a solid
 * block (so no culling) and may have alpha channels in the texture (like glass).
 * It also assumes the block can be rotated, and saves the rotation as a set of
 * FACING properties.  This MAY change in later versions to TE data though...
 *
 * @author don_bruce
 */
@EventBusSubscriber
public class BuilderBlock extends Block{
	/**Map of created blocks linked to their builder instances.  Used for interface operations.**/
	protected static final Map<ABlockBase, BuilderBlock> blockMap = new HashMap<ABlockBase, BuilderBlock>();
	
	/**Current block we are built around.**/
	protected final ABlockBase block;
	/**Holding map for block drops.  MC calls breakage code after the TE is removed, so we need to store drops 
	created during the drop checks here to ensure they actually drop when the block is broken. **/
	private static final Map<BlockPos, List<ItemStack>> dropsAtPositions = new HashMap<BlockPos, List<ItemStack>>();
	
	//TODO remove this when we figure out how to not make blocks go poof.
	private static final PropertyDirection FACING = BlockHorizontal.FACING;
	
    BuilderBlock(ABlockBase block){
		super(Material.ROCK);
		this.block = block;
		fullBlock = false;
		setHardness(block.hardness);
		setResistance(block.blastResistance);
		setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.SOUTH));
	}
    
    @Override
    public boolean hasTileEntity(IBlockState state){
    	//If our block implements the interface to be a TE, we return true.
        return block instanceof ABlockBaseTileEntity;
    }
    
	@Nullable
	@Override
    public TileEntity createTileEntity(World world, IBlockState state){
    	//Need to return a wrapper class here, not the actual TE.
		if(block instanceof IBlockFluidTankProvider){
			return getTileEntityTankWrapper(block);
		}else{
			return getTileEntityGenericWrapper(block);
		}
    }
	
	 /**
	 *  Helper method for creating new Wrapper TEs for this block.
	 *  Far better than ? all over for generics in the createTileEntity method.
	 */
	private static <TileEntityType extends ATileEntityBase<?>> BuilderTileEntity<TileEntityType> getTileEntityGenericWrapper(ABlockBase block){
		return new BuilderTileEntity<TileEntityType>();
	}
	
	 /**
	 *  Helper method for creating new Wrapper TEs for this block.
	 *  Far better than ? all over for generics in the createTileEntity method.
	 */
	private static <TileEntityType extends ATileEntityBase<?> & ITileEntityFluidTankProvider> BuilderTileEntity<TileEntityType> getTileEntityTankWrapper(ABlockBase block){
		return new BuilderTileEntityFluidTank<TileEntityType>();
	}
	
	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ){
		//Forward this click to the block.  For left-clicks we'll need to use item attack calls.
		if(block instanceof ABlockBaseTileEntity){
			if(!world.isRemote){
	    		TileEntity tile = world.getTileEntity(pos);
	    		if(tile instanceof BuilderTileEntity){
	    			if(((BuilderTileEntity<?>) tile).tileEntity != null){
	    				return ((BuilderTileEntity<?>) tile).tileEntity.interact(WrapperPlayer.getWrapperFor(player));
	    			}
	    		}
			}else{
				return true;
			}
		}
		return super.onBlockActivated(world, pos, state, player, hand, side, hitX, hitY, hitZ);
	}
    
    @Override
	public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player){
		//Returns the ItemStack that gets put in the player's inventory when they middle-click this block.
    	//This calls down into getItem, which then uses the Item class's block<->item mapping to get a block.
    	//By overriding here, we intercept those calls and return our own.  This also allows us to put NBT
    	//data on the stack based on the TE state.
    	
    	//Note that this method is only used for middle-clicking and nothing else.  Failure to return valid results
    	//here will result in air being grabbed, and no WAILA support.
    	if(block instanceof ABlockBaseTileEntity){
    		TileEntity mcTile = world.getTileEntity(pos);
    		if(mcTile instanceof BuilderTileEntity){
    			ATileEntityBase<?> tile = ((BuilderTileEntity<?>) mcTile).tileEntity;
    			if(tile != null){
    				AItemPack<?> item = tile.getItem();
    				if(item != null){
    					ItemStack stack = item.getNewStack();
    	        		WrapperNBT data = new WrapperNBT(new NBTTagCompound());
    	        		((BuilderTileEntity<?>) mcTile).tileEntity.save(data);
    	        		stack.setTagCompound(data.tag);
    	            	return stack;
    				}
    			}
    		}
    	}
    	return super.getPickBlock(state, target, world, pos, player);
    }
    
    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune){
    	//If this is a TE, drop TE drops.  Otherwise, drop normal drops.
    	if(block instanceof ABlockBaseTileEntity){
    		if(dropsAtPositions.containsKey(pos)){
    			drops.addAll(dropsAtPositions.get(pos));
    			dropsAtPositions.remove(pos);
    		}
    	}else{
    		super.getDrops(drops, world, pos, state, fortune);
    	}
    		
    }
    
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state){
    	//Forward the breaking call to the block to allow for breaking logic.
    	block.onBroken(WrapperWorld.getWrapperFor(world), new Point3d(pos.getX(), pos.getY(), pos.getZ()));
    	//This gets called before the block is broken to do logic.  Save drops to static map to be
    	//spawned during the getDrops method.  Also notify the block that it's been broken in case
    	//it needs to do operations.
    	if(block instanceof ABlockBaseTileEntity){
    		TileEntity tile = world.getTileEntity(pos);
    		if(tile instanceof BuilderTileEntity){
    			if(((BuilderTileEntity<?>) tile).tileEntity != null){
    				List<ItemStack> drops = new ArrayList<ItemStack>();
    				((BuilderTileEntity<?>) tile).tileEntity.addDropsToList(drops);
        			dropsAtPositions.put(pos, drops);
    			}
    		}
    	}
    	super.breakBlock(world, pos, state);
    }
    
    @Override
	@SuppressWarnings("deprecation")
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes, @Nullable Entity entity, boolean p_185477_7_){
    	AxisAlignedBB mcBox = getBlockBox(state, world, pos, true);
    	if(mcBox.intersects(entityBox)){
			collidingBoxes.add(mcBox);
		}
    }
    
    @Override
	@SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess access, BlockPos pos){
    	return getBlockBox(state, access, pos, false);
    }
    
    private AxisAlignedBB getBlockBox(IBlockState state, IBlockAccess access, BlockPos pos, boolean globalCoords){
    	//Gets the bounding boxes. We forward this call to the tile entity to handle if we have one.
    	//Otherwise, get the bounds from the main block, or just the standard bounds.
    	//We add-on 0.5D to offset the box to the correct location, as our blocks are centered.
    	//Bounding boxes are not offset, whereas collision are, which is what the boolean paramter is for.
    	if(block instanceof ABlockBaseTileEntity){
    		TileEntity mcTile = access.getTileEntity(pos);
    		if(mcTile instanceof BuilderTileEntity){
    			ATileEntityBase<?> tile = ((BuilderTileEntity<?>) mcTile).tileEntity;
    			if(tile != null){
    				if(globalCoords){
    					return tile.getCollisionBox().convertWithOffset(0.5D, 0.5D, 0.5D);
    				}else{
    					return tile.getCollisionBox().convertWithOffset(-pos.getX() + 0.5D, -pos.getY() + 0.5D, -pos.getZ() + 0.5D);
    				}
    			}
    		}
    	}else if(block instanceof BlockCollision){
    		if(globalCoords){
				return ((BlockCollision) block).blockBounds.convertWithOffset(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
			}else{
				return ((BlockCollision) block).blockBounds.convertWithOffset(0.5D, 0.5D, 0.5D);
			}
    	}
    	if(globalCoords){
    		return FULL_BLOCK_AABB.offset(pos);
    	}else{
    		return FULL_BLOCK_AABB;
    	}
    }

    @Override
    public int getMetaFromState(IBlockState state){
    	//Saves the state as metadata.
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    protected BlockStateContainer createBlockState(){
    	//Creates a new, default, blockstate holder.  Return the four facing directions here.
        return new BlockStateContainer(this, new IProperty[] {FACING});
    }
	
    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState state){
    	//If this is opaque, we block light.  None of our blocks are opaque and block light.
        return false;
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state){
    	//If this is a full cube, we do culling on faces and potentially connections.  None of our blocks are full cubes.
        return false;
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face){
    	//If this is SOLID, we can attach things to this block (e.g. torches).  We don't want that for any of our blocks.
        return BlockFaceShape.UNDEFINED;
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(IBlockState state){
    	//Don't render this block.  We manually render via the TE.
        return EnumBlockRenderType.INVISIBLE;
    }
    
    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos){
    	if(block instanceof ABlockBaseTileEntity){
    		TileEntity tile = world.getTileEntity(pos);
    		if(tile instanceof BuilderTileEntity){
    			if(((BuilderTileEntity<?>) tile).tileEntity != null){
    				return (int) (((BuilderTileEntity<?>) tile).tileEntity.getLightProvided()*15);
    			}
    		}
		}
		return super.getLightValue(state, world, pos);
    }
  	
  	/**
	 * Registers all blocks in the core mod, as well as any decors in packs.
	 * Also adds the respective TileEntity if the block has one.
	 */
	@SubscribeEvent
	public static void registerBlocks(RegistryEvent.Register<Block> event){
		//Create all pack items.  We need to do this here in the blocks because
		//block registration comes first, and we use the items registered to determine
		//which blocks we need to register.
		for(String packID : PackParserSystem.getAllPackIDs()){
			for(AItemPack<?> packItem : PackParserSystem.getAllItemsForPack(packID, true)){
				if(packItem.autoGenerate()){
					new BuilderItem(packItem);
				}
			}
		}
		
		//Register the TEs.
		GameRegistry.registerTileEntity(BuilderTileEntity.class, new ResourceLocation(MasterLoader.MODID, BuilderTileEntity.class.getSimpleName()));
		GameRegistry.registerTileEntity(BuilderTileEntityFluidTank.class, new ResourceLocation(MasterLoader.MODID, BuilderTileEntityFluidTank.class.getSimpleName()));
		
		//Register the IItemBlock blocks.  We cheat here and
		//iterate over all items and get the blocks they spawn.
		//Not only does this prevent us from having to manually set the blocks
		//we also pre-generate the block classes here.
		List<ABlockBase> blocksRegistred = new ArrayList<ABlockBase>();
		for(AItemBase item : BuilderItem.itemMap.keySet()){
			if(item instanceof IItemBlock){
				ABlockBase itemBlockBlock = ((IItemBlock) item).getBlock();
				if(!blocksRegistred.contains(itemBlockBlock)){
					//New block class detected.  Register it and its instance.
					BuilderBlock wrapper = new BuilderBlock(itemBlockBlock);
					String name = itemBlockBlock.getClass().getSimpleName();
					name = MasterLoader.MODID + ":" + name.substring("Block".length());
					event.getRegistry().register(wrapper.setRegistryName(name).setTranslationKey(name));
					blockMap.put(itemBlockBlock, wrapper);
					blocksRegistred.add(itemBlockBlock);
				}
			}
		}
		
		//Register the collision blocks.
		for(int i=0; i<BlockCollision.blockInstances.size(); ++i){
			BlockCollision collisionBlock = BlockCollision.blockInstances.get(i);
			BuilderBlock wrapper = new BuilderBlock(collisionBlock);
			String name = collisionBlock.getClass().getSimpleName();
			name = MasterLoader.MODID + ":" + name.substring("Block".length()) + i;
			event.getRegistry().register(wrapper.setRegistryName(name).setTranslationKey(name));
			blockMap.put(collisionBlock, wrapper);
		}
	}
}
