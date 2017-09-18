package br.net.fabiozumbi12.UltimateChat.Bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import me.clip.placeholderapi.PlaceholderAPI;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import br.net.fabiozumbi12.UltimateChat.Bukkit.UCLogger.timingType;
import br.net.fabiozumbi12.UltimateChat.Bukkit.API.PostFormatChatMessageEvent;
import br.net.fabiozumbi12.UltimateChat.Bukkit.API.SendChannelMessageEvent;

public class UCMessages {

	private static HashMap<String, String> registeredReplacers = new HashMap<String,String>();
	private static String[] defFormat = new String[0];	
	
	protected static boolean sendFancyMessage(String[] format, String msg, UCChannel channel, CommandSender sender, CommandSender tellReceiver){
		//Execute listener:
		HashMap<String,String> tags = new HashMap<String,String>();
		for (String str:UChat.get().getConfig().getStringList("api.legendchat-tags")){
			tags.put(str, str);
		}
		SendChannelMessageEvent event = new SendChannelMessageEvent(tags, format, sender, channel, msg);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()){
			return event.getCancelIncomingChat();
		}
			
		boolean cancel = event.getCancelIncomingChat();
		//String toConsole = "";
		registeredReplacers = event.getResgisteredTags();
		defFormat = event.getDefFormat();
		String evmsg = event.getMessage();
				
		HashMap<CommandSender, UltimateFancy> msgPlayers = new HashMap<CommandSender, UltimateFancy>();
		evmsg = composeColor(sender,evmsg);
						
		if (event.getChannel() != null){					
			
			UCChannel ch = event.getChannel();		
			
			if (sender instanceof Player && !ch.availableWorlds().isEmpty() && !ch.availableInWorld(((Player)sender).getWorld())){
				UChat.get().getLang().sendMessage(sender, UChat.get().getLang().get("channel.notavailable").replace("{channel}", ch.getName()));
				return cancel;
			}
			
			if (!UCPerms.channelWritePerm(sender, ch)){
				UChat.get().getLang().sendMessage(sender, UChat.get().getLang().get("channel.nopermission").replace("{channel}", ch.getName()));
				return cancel;
			}
			
			if (ch.isMuted(sender.getName())){
				UChat.get().getLang().sendMessage(sender, "channel.muted");
				return cancel;
			}
			
			if (!UCPerms.hasPerm(sender, "bypass.cost") && UChat.get().getVaultEco() != null && sender instanceof Player && ch.getCost() > 0){
				if (UChat.get().getVaultEco().getBalance((Player)sender, ((Player)sender).getWorld().getName()) < ch.getCost()){
					UChat.get().getLang().sendMessage(sender, UChat.get().getLang().get("channel.cost").replace("{value}", ""+ch.getCost()));
					return cancel;
				} else {
					UChat.get().getVaultEco().withdrawPlayer((Player)sender, ((Player)sender).getWorld().getName(), ch.getCost());
				}
			}
			
			List<Player> receivers = new ArrayList<Player>();	
			int noWorldReceived = 0;
			int vanish = 0;
			
			//put sender
			msgPlayers.put(sender, sendMessage(sender, sender, evmsg, ch, false));
			
			if (ch.getDistance() > 0 && sender instanceof Player){			
				for (Entity ent:((Player)sender).getNearbyEntities(ch.getDistance(), ch.getDistance(), ch.getDistance())){
					if (ent instanceof Player && UCPerms.channelReadPerm((Player)ent, ch)){	
						Player receiver = (Player) ent;				
						if (!ch.availableWorlds().isEmpty() && !ch.availableInWorld(receiver.getWorld())){
							continue;
						}
						if (ch.isIgnoring(receiver.getName())){
							continue;
						}
						if (isIgnoringPlayers(receiver.getName(), sender.getName())){
							noWorldReceived++;
							continue;
						}
						if (!((Player)sender).canSee(receiver)){
							vanish++;
						}
						if ((ch.neeFocus() && ch.isMember(receiver)) || !ch.neeFocus()){					
							msgPlayers.put(receiver, sendMessage(sender, receiver, evmsg, ch, false));
							receivers.add((Player)ent);
						}
					}				
				}
			} else {
				for (Player receiver:UChat.get().getServer().getOnlinePlayers()){	
					if (receiver.equals(sender) || !UCPerms.channelReadPerm(receiver, ch) || (!ch.crossWorlds() && (sender instanceof Player && !receiver.getWorld().equals(((Player)sender).getWorld())))){				
						continue;
					}
					if (!ch.availableWorlds().isEmpty() && !ch.availableInWorld(receiver.getWorld())){
						continue;
					}
					if (ch.isIgnoring(receiver.getName())){
						continue;
					}
					if (isIgnoringPlayers(receiver.getName(), sender.getName())){
						noWorldReceived++;
						continue;
					}
					if (sender instanceof Player && (!((Player)sender).canSee(receiver))){
						vanish++;
					} else {
						noWorldReceived++;
					}					
					if ((ch.neeFocus() && ch.isMember(receiver)) || !ch.neeFocus()){
						msgPlayers.put(receiver, sendMessage(sender, receiver, evmsg, ch, false));
						receivers.add(receiver);
					}				
				}
			}	
									
			//chat spy
			for (Player receiver:UChat.get().getServer().getOnlinePlayers()){			
				if (!receiver.equals(sender) && !receivers.contains(receiver) && !receivers.contains(sender) && UChat.get().isSpy.contains(receiver.getName())){	
					String spyformat = UChat.get().getConfig().getString("general.spy-format");
					spyformat = spyformat.replace("{output}", ChatColor.stripColor(sendMessage(sender, receiver, evmsg, ch, true).toOldFormat()));					
					receiver.sendMessage(ChatColor.translateAlternateColorCodes('&', spyformat));
				}
			}
			if (ch.getDistance() == 0 && noWorldReceived <= 0){
				if (ch.getReceiversMsg()){
					UChat.get().getLang().sendMessage(sender, "channel.noplayer.world");
				}
			}		
			if ((receivers.size()-vanish) <= 0){
				if (ch.getReceiversMsg()){
					UChat.get().getLang().sendMessage(sender, "channel.noplayer.near");	
				}
			}
			
		} else {						
			//send tell
			channel = new UCChannel("tell");
			
			//send spy			
			for (Player receiver:UChat.get().getServer().getOnlinePlayers()){			
				if (!receiver.equals(tellReceiver) && !receiver.equals(sender) && UChat.get().isSpy.contains(receiver.getName())){
					String spyformat = UChat.get().getConfig().getString("general.spy-format");
					if (isIgnoringPlayers(tellReceiver.getName(), sender.getName())){
						spyformat = UChat.get().getLang().get("chat.ignored")+spyformat;
					}
					spyformat = spyformat.replace("{output}", ChatColor.stripColor(sendMessage(sender, tellReceiver, evmsg, channel, true).toOldFormat()));					
					receiver.sendMessage(ChatColor.translateAlternateColorCodes('&', spyformat));
				}
			}
			msgPlayers.put(sender, sendMessage(sender, tellReceiver, evmsg, channel, false));
			if (!isIgnoringPlayers(tellReceiver.getName(), sender.getName())){
				msgPlayers.put(tellReceiver, sendMessage(sender, tellReceiver, evmsg, channel, false));
			}			
			if (isIgnoringPlayers(tellReceiver.getName(), sender.getName())){
				msgPlayers.put(UChat.get().getServer().getConsoleSender(), new UltimateFancy(UChat.get().getLang().get("chat.ignored")+msgPlayers.values().stream().findAny().get().toOldFormat()));
			}
		}
		
		if (!msgPlayers.keySet().contains(UChat.get().getServer().getConsoleSender())){
			msgPlayers.put(UChat.get().getServer().getConsoleSender(), msgPlayers.values().stream().findAny().get());
		}
		
		//fire post event
		PostFormatChatMessageEvent postEvent = new PostFormatChatMessageEvent(sender, msgPlayers, channel, msg);
		Bukkit.getPluginManager().callEvent(postEvent); 
		if (postEvent.isCancelled()){
			return cancel;
		}
		
		msgPlayers.forEach((send,text)->{			
			UChat.get().getUCLogger().timings(timingType.END, "UCMessages#send()|before send");
			text.send(send);
			UChat.get().getUCLogger().timings(timingType.END, "UCMessages#send()|after send");
		});	
				
		if (channel != null && UChat.get().getUCJDA() != null){
			if (channel.isTell()){
				UChat.get().getUCJDA().sendTellToDiscord(msgPlayers.get(sender).toOldFormat());
			} else {
				UChat.get().getUCJDA().sendToDiscord(sender, evmsg, channel);
			}
		}
		
		return cancel;
	}
	
	private static String composeColor(CommandSender sender, String evmsg){
		evmsg = ChatColor.translateAlternateColorCodes('&', evmsg);	
		if (sender instanceof Player){			
			if (!UCPerms.hasPerm((Player)sender, "chat.color")){
				evmsg = evmsg.replaceAll("(?i)§([a-f0-9r])", "&$1");
			}
			if (!UCPerms.hasPerm((Player)sender, "chat.color.formats")){
				evmsg = evmsg.replaceAll("(?i)§([l-o])", "&$1");
			}
			if (!UCPerms.hasPerm((Player)sender, "chat.color.magic")){
				evmsg = evmsg.replaceAll("(?i)§([k])", "&$1");
			}
		}	
		return evmsg;
	}
	
	public static boolean isIgnoringPlayers(String p, String victim){
		Player play = Bukkit.getPlayer(p);
		if (play != null && (play.isOp() || play.hasPermission("uchat.admin"))){
			return false;
		}
		
		List<String> list = new ArrayList<String>();
		if (UChat.get().ignoringPlayer.containsKey(p)){
			list.addAll(UChat.get().ignoringPlayer.get(p));			
		}		
		return list.contains(victim);
	}
	
	public static void ignorePlayer(String p, String victim){
		List<String> list = new ArrayList<String>();
		if (UChat.get().ignoringPlayer.containsKey(p)){
			list.addAll(UChat.get().ignoringPlayer.get(p));
		}
		list.add(victim);
		UChat.get().ignoringPlayer.put(p, list);
	}
	
	public static void unIgnorePlayer(String p, String victim){
		List<String> list = new ArrayList<String>();
		if (UChat.get().ignoringPlayer.containsKey(p)){
			list.addAll(UChat.get().ignoringPlayer.get(p));
		}
		list.remove(victim);
		UChat.get().ignoringPlayer.put(p, list);
	}
	
	@SuppressWarnings("deprecation")
	private static UltimateFancy sendMessage(CommandSender sender, CommandSender receiver, String msg, UCChannel ch, boolean isSpy){		
		UltimateFancy fanci = new UltimateFancy();
				
		if (!ch.getName().equals("tell")){
			String[] defaultBuilder = UChat.get().getConfig().getDefBuilder();
			if (ch.useOwnBuilder()){
				defaultBuilder = ch.getBuilder();
			}
			
			for (String tag:defaultBuilder){
				if (UChat.get().getConfig().getString("tags."+tag+".format") == null){
					fanci.text(tag).next();
					continue;
				}
				
				String perm = UChat.get().getConfig().getString("tags."+tag+".permission");
				String format = UChat.get().getConfig().getString("tags."+tag+".format");
				String execute = UChat.get().getConfig().getString("tags."+tag+".click-cmd");
				String suggest = UChat.get().getConfig().getString("tags."+tag+".suggest-cmd");
				List<String> messages = UChat.get().getConfig().getStringList("tags."+tag+".hover-messages");				
				List<String> showWorlds = UChat.get().getConfig().getStringList("tags."+tag+".show-in-worlds");
				List<String> hideWorlds = UChat.get().getConfig().getStringList("tags."+tag+".hide-in-worlds");
				
				//check perm
				if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)){
					continue;
				}
				
				//check show or hide in world
				if (sender instanceof Player){
					if (!showWorlds.isEmpty() && !showWorlds.contains(((Player)sender).getWorld().getName())){
						continue;
					}
					if (!hideWorlds.isEmpty() && hideWorlds.contains(((Player)sender).getWorld().getName())){
						continue;
					}
				}
							
				String tooltip = "";
				for (String tp:messages){
					tooltip = tooltip+"\n"+tp;
				}
				if (tooltip.length() > 2){
					tooltip = tooltip.substring(1);
				}			
							
				if (execute != null && execute.length() > 0){
					fanci.clickRunCmd(formatTags(tag, "/"+execute, sender, receiver, msg, ch));
				}
				
				if (suggest != null && suggest.length() > 0){
					fanci.clickSuggestCmd(formatTags(tag, suggest, sender, receiver, msg, ch));
				}
				
				if (tag.equals("message") && (!msg.equals(mention(sender, receiver, msg)) || msg.contains(UChat.get().getConfig().getString("general.item-hand.placeholder")))){					
					tooltip = formatTags("", tooltip, sender, receiver, msg, ch);	
					format = formatTags(tag, format, sender, receiver, msg, ch);
					
					if (UChat.get().getConfig().getBool("general.item-hand.enable") && msg.contains(UChat.get().getConfig().getString("general.item-hand.placeholder")) && sender instanceof Player){					
						fanci.text(format).hoverShowItem(((Player)sender).getItemInHand()).next();
					} else if (!msg.equals(mention(sender, receiver, msg)) && UChat.get().getConfig().getString("mention.hover-message").length() > 0 && StringUtils.containsIgnoreCase(msg, receiver.getName())){
						tooltip = formatTags("", UChat.get().getConfig().getString("mention.hover-message"), sender, receiver, msg, ch);						
						fanci.text(format).hoverShowText(tooltip).next();
					} else if (tooltip.length() > 0){	
						fanci.text(format).hoverShowText(tooltip).next();
					} else {
						fanci.text(format).next();						
					}				
				} else {					
					format = formatTags(tag, format, sender, receiver, msg, ch);
					tooltip = formatTags("", tooltip, sender, receiver, msg, ch);					
					if (tooltip.length() > 0){		
						fanci.text(format).hoverShowText(tooltip).next();
					} else {			
						fanci.text(format).next();
					}
				}
			}			
		} else {
			//if tell
			String prefix = UChat.get().getConfig().getString("tell.prefix");
			String format = UChat.get().getConfig().getString("tell.format");
			List<String> messages = UChat.get().getConfig().getStringList("tell.hover-messages");
						
			String tooltip = "";
			for (String tp:messages){
				tooltip = tooltip+"\n"+tp;
			}
			if (tooltip.length() > 2){
				tooltip = tooltip.substring(1);
			}
						
			
			prefix = formatTags("", prefix, sender, receiver, msg, ch);						
			format = formatTags("tell", format, sender, receiver, msg, ch);
			tooltip = formatTags("", tooltip, sender, receiver, msg, ch);
			
			if (tooltip.length() > 0){
				fanci.text(format).hoverShowText(tooltip).next();
			} else {
				fanci.text(prefix).next();
			}			
			fanci.text(ChatColor.stripColor(format)).next();			
		}	
		return fanci;
	}
			
	public static String mention(Object sender, CommandSender receiver, String msg) {
		if (UChat.get().getConfig().getBool("mention.enable")){
		    for (Player p:UChat.get().getServer().getOnlinePlayers()){			
				if (StringUtils.containsIgnoreCase(msg, p.getName())){
					if (receiver instanceof Player && receiver.equals(p)){
						
						String mentionc = UChat.get().getConfig().getColorStr("mention.color-template").replace("{mentioned-player}", p.getName());
						mentionc = formatTags("", mentionc, sender, receiver, "", new UCChannel("mention"));
						
						if (msg.contains(mentionc) || sender instanceof CommandSender && !UCPerms.hasPerm((CommandSender)sender, "chat.mention")){
							msg = msg.replaceAll("(?i)\\b"+p.getName()+"\\b", p.getName());
							continue;
						}
											
						for (Sound sound:Sound.values()){
							if (StringUtils.containsIgnoreCase(sound.toString(),UChat.get().getConfig().getString("mention.playsound")) && !msg.contains(mentionc)){
								p.playSound(p.getLocation(), sound, 1F, 1F);
								break;
							}
						}
						msg = msg.replace(mentionc, p.getName());	
						msg = msg.replaceAll("(?i)\\b"+p.getName()+"\\b", mentionc);
					} else {
						msg = msg.replaceAll("(?i)\\b"+p.getName()+"\\b", p.getName());
					}					
				}
			}
		}				
		return msg;
	}
	
	@SuppressWarnings("deprecation")
	public static String formatTags(String tag, String text, Object cmdSender, Object receiver, String msg, UCChannel ch){	
		if (receiver instanceof CommandSender && tag.equals("message")){			
			text = text.replace("{message}", mention(cmdSender, (CommandSender)receiver, msg));
			if (UChat.get().getConfig().getBool("general.item-hand.enable")){
				text = text.replace(UChat.get().getConfig().getString("general.item-hand.placeholder"), formatTags("",ChatColor.translateAlternateColorCodes('&', UChat.get().getConfig().getString("general.item-hand.format")),cmdSender, receiver, msg, ch));
			}			
		} else {
			text = text.replace("{message}", msg);
		}
		if (tag.equals("message") && !UChat.get().getConfig().getBool("general.enable-tags-on-messages")){
			return text;
		}
		text = text.replace("{ch-color}", ch.getColor())
		.replace("{ch-name}", ch.getName())
		.replace("{ch-alias}", ch.getAlias());		
		if (cmdSender instanceof CommandSender){
			text = text.replace("{playername}", ((CommandSender)cmdSender).getName())
					.replace("{receivername}", ((CommandSender)receiver).getName());
		} else {
			text = text.replace("{playername}", (String)cmdSender)
					.replace("{receivername}", (String)receiver);
		}
		for (String repl:registeredReplacers.keySet()){
			if (registeredReplacers.get(repl).equals(repl)){
				text = text.replace(repl, "");
				continue;
			}
			text = text.replace(repl, registeredReplacers.get(repl));			
		}		
		String def = "";
		for (int i = 0; i < defFormat.length; i++){
			text = text.replace("{default-format-"+i+"}", defFormat[i]);
			def = def+" "+defFormat[i];
		}		
		if (def.length() > 0){
			text = text.replace("{default-format-full}", def.substring(1));
		}		
		
		if (cmdSender instanceof Player){
			Player sender = (Player)cmdSender;
			
			text = text.replace("{nickname}", sender.getDisplayName())					
					.replace("{world}", sender.getWorld().getName());
			
			//replace item hand			
			text = text.replace(UChat.get().getConfig().getString("general.item-hand.placeholder"), ChatColor.translateAlternateColorCodes('&', UChat.get().getConfig().getString("general.item-hand.format")));
			if (!sender.getItemInHand().getType().equals(Material.AIR)){
				ItemStack item = sender.getItemInHand();
				
				text = text.replace("{hand-durability}", String.valueOf(item.getDurability()));
				if (item.hasItemMeta()){
					ItemMeta meta = item.getItemMeta();
					if (UCUtil.getBukkitVersion() >= 1112 && meta.hasLocalizedName()){
						text = text.replace("{hand-name}", item.getItemMeta().getLocalizedName());
					} else if (meta.hasDisplayName()){
						text = text.replace("{hand-name}", item.getItemMeta().getDisplayName());
					} else {
						text = text.replace("{hand-name}", UCUtil.capitalize(item.getType().toString()));
					}
					if (meta.hasLore()){
						StringBuilder lorestr = new StringBuilder();
						for (String lore:meta.getLore()){
							lorestr.append("\n "+lore);							
						}	
						if (lorestr.length() >= 2){
							text = text.replace("{hand-lore}", lorestr.toString().substring(0, lorestr.length()-1));
						}
					}
					if (meta.hasEnchants()){
						StringBuilder str = new StringBuilder();
						for (Entry<Enchantment, Integer> enchant:meta.getEnchants().entrySet()){
							str.append("\n "+enchant.getKey().getName()+": "+enchant.getValue());							
						}	
						if (str.length() >= 2){
							text = text.replace("{hand-enchants}", str.toString().substring(0, str.length()-1));
						}							
					}					
				}
				text = text.replace("{hand-amount}", String.valueOf(item.getAmount()));
				text = text.replace("{hand-name}", UCUtil.capitalize(item.getType().toString()));
				text = text.replace("{hand-type}", UCUtil.capitalize(item.getType().toString()));
			} else {
				text = text.replace("{hand-name}", UChat.get().getLang().get("chat.emptyslot"));
				text = text.replace("{hand-type}", "Air");
			}
			
			if (UChat.get().getVaultChat() != null){			
				text = text
						.replace("{group-suffix}", UChat.get().getVaultChat().getPlayerSuffix(sender))
						.replace("{group-prefix}", UChat.get().getVaultChat().getPlayerPrefix(sender));
				String[] pgs = UChat.get().getVaultChat().getPlayerGroups(sender.getWorld().getName(), sender);
				if (pgs.length > 0){
					StringBuilder gprefixes = new StringBuilder();
					StringBuilder gsuffixes = new StringBuilder();
					for (String g:pgs){
						gprefixes.append(UChat.get().getVaultChat().getGroupPrefix(sender.getWorld().getName(), g));
						gsuffixes.append(UChat.get().getVaultChat().getGroupSuffix(sender.getWorld().getName(), g));
					}
					text = text
							.replace("{player-groups-prefixes}", gprefixes.toString())
							.replace("{player-groups-suffixes}", gsuffixes.toString());
				}
			}			
			if (UChat.get().getVaultEco() != null){			
				text = text
						.replace("{balance}", ""+UChat.get().getVaultEco().getBalance(sender,sender.getWorld().getName()));
			}	
			if (UChat.get().getVaultPerms() != null){	
				String[] pgs = UChat.get().getVaultPerms().getPlayerGroups(sender.getWorld().getName(), sender);
				if (pgs.length > 0){
					StringBuilder groups = new StringBuilder();
					for (String g:pgs){
						groups.append(g+",");
					}
					text = text.replace("{player-groups}", groups.toString().substring(0, groups.length()-1));
					
				}
				text = text
						.replace("{prim-group}", UChat.get().getVaultPerms().getPrimaryGroup(sender.getWorld().getName(),sender));
						
			}
			if (UChat.SClans){		
				ClanPlayer cp = UChat.sc.getClanPlayer(sender.getUniqueId());
				if (cp != null){
					text = text
							.replace("{clan-tag}", cp.getTag())
							.replace("{clan-name}", checkEmpty(cp.getClan().getName()))
							.replace("{clan-kdr}", ""+cp.getKDR())
							.replace("{clan-rank}", checkEmpty(cp.getRank()))
							.replace("{clan-totalkdr}", ""+cp.getClan().getTotalKDR())
							.replace("{clan-deaths}", ""+cp.getDeaths())
							.replace("{clan-isleader}", ""+cp.isLeader())
							.replace("{clan-totalkdr}", ""+cp.getClan().getTotalKDR())
							.replace("{clan-ctag}", cp.getClan().getColorTag());
				}
			}
			if (UChat.Factions){		
				text = UCFactionsHook.formatFac(text, sender, receiver);
			}
			if (UChat.MarryReloded && sender.hasMetadata("marriedTo")){
				String partner = sender.getMetadata("marriedTo").get(0).asString();		
				String prefix = UChat.mapi.getBukkitConfig("config.yml").getString("chat.status-format");
				UChat.mapi.getMPlayer(sender.getUniqueId()).getGender().getChatPrefix();
				prefix = prefix
						.replace("{icon:male}", "♂")
						.replace("{icon:female}", "♀")
						.replace("{icon:genderless}", "⚤")
						.replace("{icon:heart}", "❤");
				String gender = UChat.mapi.getMPlayer(sender.getUniqueId()).getGender().getChatPrefix();
				gender = gender
						.replace("{icon:male}", "♂")
						.replace("{icon:female}", "♀")
						.replace("{icon:genderless}", "⚤")
						.replace("{icon:heart}", "❤");
				text = text
						.replace("{marry-partner}", partner)
						.replace("{marry-prefix}", prefix)
						.replace("{marry-suffix}", gender);
			}
			if (UChat.MarryMaster){			
				if (UChat.mm.HasPartner(sender)){
					text = text
							.replace("{marry-partner}", UChat.mm.DB.GetPartner(sender))
							.replace("{marry-prefix}", UChat.mm.config.GetPrefix().replace("<heart>", ChatColor.RED + "❤" + ChatColor.WHITE))
							.replace("{marry-suffix}", UChat.mm.config.GetSuffix().replace("<heart>", ChatColor.RED + "❤" + ChatColor.WHITE));
				}
			}			
			if (UChat.PlaceHolderAPI){
				text = PlaceholderAPI.setPlaceholders(sender, text);
				if (tag.equals("message")){					
					text = composeColor(sender,text);
				}
			}
		}		
		
		if (cmdSender instanceof CommandSender){
			text = text.replace("{nickname}", UChat.get().getConfig().getString("general.console-tag").replace("{console}", ((CommandSender)cmdSender).getName()));
		} else {
			text = text.replace("{nickname}", UChat.get().getConfig().getString("general.console-tag").replace("{console}", (String)cmdSender));
		}			
		
		//colorize tags (not message)
		if (!tag.equals("message")){
			text = ChatColor.translateAlternateColorCodes('&', text);
		}		
		
		//remove blank items		
		text = text.replaceAll("\\{.*\\}", "");		
		
		//remove remain PlaceholderAPI
		if (UChat.get().getConfig().getBool("general.remove-unnused-placeholderapi")){
			text = text.replaceAll("\\%.*\\%", "");	
		}		
		
		if (!tag.equals("message")){
			for (String rpl:UChat.get().getConfig().getStringList("general.remove-from-chat")){
				text = text.replace(ChatColor.translateAlternateColorCodes('&', rpl), "");
			}
		}		
		if (text.equals(" ") || text.equals("  ")){
			return text = "";
		}
		return text;
	}
		
	private static String checkEmpty(String tag){
		if (tag.length() <= 0){
			return UChat.get().getLang().get("tag.notset");
		}
		return tag;
	}	
}
