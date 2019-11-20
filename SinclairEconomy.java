package com.peteroertel;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

public class SinclairEconomy extends JavaPlugin {
	
	public ArrayList<EcoInvItem> serverInventory;
	
	public SinclairEconomy() {
		
	}

	public SinclairEconomy(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
		super(loader, description, dataFolder, file);
	}
	
	/**
	 * Determines how many items a player needs to buy/sell before reaching the threshold at which the price of the item changes. The values used here are determined by the contents of config.yml, but can be changed with /seadmin commands.
	 * @param currentAmount The current amount of an item we have on hand
	 * @param defaultValue The default value of the item, read from config.yml probably
	 * @param positiveThreshold True if we're looking up for a threshold, False if we're looking down.
	 * @return The distance to the next threshold in the direction set by {@code positiveTheshold}
	 */
	public int nextThreshold(int currentAmount, int defaultValue, boolean positiveThreshold) {
		int thresholdDistance = 0;
		int invStep = this.getConfig().getInt("thresholdStep")/defaultValue;
		int threshRemainder = currentAmount%invStep;
		
		if(positiveThreshold) {
			thresholdDistance = invStep - threshRemainder;
		} else {
			thresholdDistance = threshRemainder;
		}
		
		return thresholdDistance;
	}
	
	public int currentValue(String m) {
		int defaultValue = this.getConfig().getInt(m + ".defaultvalue");
		getLogger().info("Default value is " + defaultValue);
		
		//Quickly return 0 if the default is zero so we don't throw an exception in the division later
		if(defaultValue == 0) return 0;
		
		int currentAmount = this.getConfig().getInt(m + ".amount");
		getLogger().info("The amount on hand is " + currentAmount);
		
		int thresholdStep = this.getConfig().getInt("ThresholdStep");
		getLogger().info("The config threshold step is " + thresholdStep);
		
		//Do the complicated math that I hate to get the current value
		int currentValue = (int) (defaultValue - Math.floor(currentAmount/(thresholdStep/defaultValue)));
		//If the current value would be less than one, return one instead
		return currentValue < 1 ? 1 : currentValue;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		//Real quick, before we do anything else, if this sender isn't in the config yet, add them now with a money value of 0.
		//I can't find a good way to do this since it looks like unregistered players will return a default 0 if they don't exist.
		//So we're just going to check for that and pop a zero in the config, even if the sender already exists there.
		if(getConfig().getInt("Players." + sender.getName()) == 0) {
			getLogger().info("Got a command from a player with $0, registering " + sender.getName());
			getConfig().set("Players." + sender.getName(), 0);
		}
		
		//Catch all the /se commands
		//Just about everything a normal player does will be through this
		if(command.getName().equalsIgnoreCase("se")) {
			//Send a fail message to the user if they don't give us any arguments
			if(args.length < 1) {
				sender.sendMessage("Not enough arguments given.");
				sender.sendMessage("Use (/se sell) for information on selling to the server.");
				sender.sendMessage("Use (/se buy) for information on buying from the server.");
				sender.sendMessage("Use (/se value) for infomation on checking the inventory and value of items");
				return true;
			}
			
			//The user has sent us a request to sell something
			if(args[0].equalsIgnoreCase("sell")) {
				if(sender instanceof ConsoleCommandSender) {
					sender.sendMessage("Note: Console has unlimited inventory to sell from.");
					sender.sendMessage("However, you can use /seadmin to better control server inventory amounts.");
				}
				//Send a fail message if we didn't get any further arguments
				if(args.length < 3) {
					sender.sendMessage("Not enough arguments given.");
					sender.sendMessage("Usage: /se sell itemname amount or /se sell hand amount");
					return true;
				}
				//The user has sent us enough args, so let's sell some stuff
				else if(args.length == 3) {
					//Check to see if the given arg is a valid itemname. If not, fail out now
					if(Material.matchMaterial(args[1]) == null) return true;
					
					//It's good, so store it
					Material materialToSell = Material.matchMaterial(args[1]);
					
					//Check to make sure the third argument is an integer
					//int amount represents how much material the player wants to sell, and shouldn't be touched after it's set
					//I'm setting it to final for now, unless I can come up with a good reason not to
					final int amount;
					try {
						amount = Integer.parseInt(args[2]);
					} catch (NumberFormatException e) {
						sender.sendMessage("Third argument isn't a valid number.");
						return true;
					}
					
					//Check to make sure the amount given is positive
					if(amount <= 0) {
						sender.sendMessage("Amount must be positive.");
						return true;
					}
					
					//If the sender is the console, skip all this other crap and just add the value to the config
					if(sender instanceof ConsoleCommandSender) {
						getConfig().set(materialToSell.name() + ".amount", getConfig().getInt(materialToSell.name() + ".amount") + amount);
						sender.sendMessage("Added " + args[2] + " " + args[1] + " to server inventory.");
						return true;
					}
					
					//The sender is a player (probably, we could check but **** it we're fine), so let's check to make sure they have what they're trying to sell
					else {
						//Pull down the player's current inventory
						Inventory i = ((Player) sender).getInventory();
						
						//Check to see if we've got enough stuff to sell
						//I couldn't find a good way to do this easily, unfortunately
						//I would use containsAtLeast(), but it compares against a given itemstack
						//The player may have itemstacks of all different amounts in their inventory, so that method won't work
						//So we're going to pull down a hashmap of all the itemstacks with the given material and just step through that ourselves
						int playerHas = 0;
						HashMap<Integer, ? extends ItemStack> iMaterials = i.all(materialToSell);
						Iterator iterator = iMaterials.entrySet().iterator();
						while(iterator.hasNext()) {
							Map.Entry mapElement = (Map.Entry) iterator.next();
							int stackAmount = (int) mapElement.getValue();
							playerHas += stackAmount;
						}
						//Man that code sucks. All right let's check it
						if(playerHas < amount) {
							sender.sendMessage("You don't have that much " + materialToSell.name());
							return true;
						}
						//Do all the logic for selling if we have enough
						//Man this is going to get complicated
						else {
							//First, we need to know how much the server already has on hand
							int serverAmount = getConfig().getInt(materialToSell.name() + ".amount");
							
							//We also need to know what the config says is the default value of this item
							int defaultValue = getConfig().getInt(materialToSell.name() + ".defaultvalue");
							
							//Now we can step through selling the items, changing the profit of each item as the 
							//amount the server has on hand causes it to decrease.
							//The amount that the player wants to sell was pulled in as a final, so we'll use a seperate variable to 
							//decrement the amount left to sell here.
							int amountLeftToSell = amount;
							
							//We also need to keep track of how much money to give the player at the end
							int profit = 0;
							
							//Okay, now we'll iterate selling items until amountLeftToSell hits zero
							while(amountLeftToSell > 0) {
								//See how much we can sell this step before we hit a threshold
								int distance = nextThreshold(serverAmount, defaultValue, true);
								
								//We also need to know how much to credit the player for each item sold this step
								
							}
						}
					}
				}
				//Too many args! Fail out.
				else if(args.length > 3) {
					sender.sendMessage("Too many arguments.");
					sender.sendMessage("Usage: /se sell itemname amount or /se sell hand amount");
					return true;
				}
			}
			//The user wants to buy something
			else if(args[0].equalsIgnoreCase("buy")) {
				if(sender instanceof ConsoleCommandSender) {
					sender.sendMessage("Note: The console can buy unlimited items.");
					sender.sendMessage("However, you can use /seadmin to better control server inventory amounts.");
				}
				
				//Send a fail message if we didn't get any further arguments
				if(args.length < 3) {
					sender.sendMessage("Not enough arguments given.");
					sender.sendMessage("Usage: /se sell itemname amount or /se sell hand amount");
					return true;
				}
				//The user has sent us enough args, so let's sell some stuff
				else if(args.length == 3) {
					//Check to see if the given arg is a valid itemname. If not, fail out now
					if(Material.matchMaterial(args[1]) == null) return true;
					
					//Check to make sure the third argument is an integer
					int amount;
					try {
						amount = Integer.parseInt(args[2]);
					} catch (NumberFormatException e) {
						sender.sendMessage("Third argument isn't a valid number.");
						return true;
					}
					
					//Check to make sure the amount given is positive
					if(amount <= 0) {
						sender.sendMessage("Amount must be positive.");
						return true;
					}
					
					//If the sender is the console, skip all this other crap and just add the value to the config
					if(sender instanceof ConsoleCommandSender) {
						getConfig().set(args[1].toUpperCase() + ".amount", getConfig().getInt(args[1].toUpperCase() + ".amount") - amount);
						sender.sendMessage("Removed " + args[2] + " " + args[1] + " from the server inventory.");
						
						//We might have just gone negative, so reset back to zero if we did
						if(getConfig().getInt(args[1].toUpperCase() + ".amount") < 0) {
							getConfig().set(args[1].toUpperCase() + ".amount", 0);
							sender.sendMessage("You just over-bought, so we're setting the amount back to zero.");
						}
						return true;
					}
				}
				//Too many args! Fail out.
				else if(args.length > 3) {
					sender.sendMessage("Too many arguments.");
					sender.sendMessage("Usage: /se sell itemname amount or /se sell hand amount");
					return true;
				}
				
			}
			//The user want's to check the value of an item
			else if(args[0].equalsIgnoreCase("value")) {
				//Send a fail message if we don't have enough arguments
				if(args.length != 2) {
					sender.sendMessage("Incorrect arguments");
					sender.sendMessage("Use /value <itemname|itemid> or /se value hand");
					return true;
				}
				//We have the right amount of arguments to proceed
				else {
					//Check if the next argument was asking to value check the currently held item
					if(args[1].equalsIgnoreCase("hand")) {
						//Fail out if this command came from the console
						if(sender instanceof ConsoleCommandSender) {
							sender.sendMessage("Can't check the value of what's in your hand if you don't have hands.");
							return true;
						}
						//Proceed now that we're pretty sure the sender is a logged in player
						else {
							
						}
					} 
					//Check to see if we got a valid itemid or itemname
					else if(Material.matchMaterial(args[1]) != null) {
						String m = args[1];
						int value = currentValue(m.toUpperCase());
						sender.sendMessage(m + " buys/sells at $" + value);
						return true;
					}
					//Second argument is garbage, fail out
					else {
						sender.sendMessage("Invalid item argument.");
						return true;
					}
				}
			}
			//Send a fail message if the user sent us garbage args
			else {
				sender.sendMessage("Invalid arguments");
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void onEnable() {
		getLogger().info("Booting up Sinclair Economy");
		this.saveDefaultConfig();
	}
	
	@Override
	public void onDisable() {
		getLogger().info("Saving the config for Sinclair Economy");
		this.saveConfig();
	}

}
