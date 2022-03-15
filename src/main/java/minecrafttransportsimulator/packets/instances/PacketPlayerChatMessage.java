package minecrafttransportsimulator.packets.instances;

import io.netty.buffer.ByteBuf;
import minecrafttransportsimulator.jsondefs.JSONConfigLanguage;
import minecrafttransportsimulator.jsondefs.JSONConfigLanguage.LanguageEntry;
import minecrafttransportsimulator.mcinterface.WrapperPlayer;
import minecrafttransportsimulator.mcinterface.WrapperWorld;
import minecrafttransportsimulator.packets.components.APacketPlayer;

/**Packet used for sending the player chat messages from the server.  Mainly for informing them
 * about things they did to a vehicle they interacted with.  Do NOT send this packet to the server
 * or it will crash when it tries to display chat messages on something without a screen!
 * 
 * @author don_bruce
 */
public class PacketPlayerChatMessage extends APacketPlayer{
	private final LanguageEntry language;
	
	public PacketPlayerChatMessage(WrapperPlayer player, LanguageEntry language){
		super(player);
		this.language = language;
	}
	
	public PacketPlayerChatMessage(ByteBuf buf){
		super(buf);
		this.language = JSONConfigLanguage.coreEntries.get(readStringFromBuffer(buf));
	}
	
	@Override
	public void writeToBuffer(ByteBuf buf){
		super.writeToBuffer(buf);
		writeStringToBuffer(language.key, buf);
	}
	
	@Override
	public void handle(WrapperWorld world, WrapperPlayer player){
		player.displayChatMessage(language);
	}
}
