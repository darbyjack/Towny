package com.palmergames.bukkit.towny.tasks;

import com.earth2me.essentials.Essentials;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyTimerHandler;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.Translation;
import com.palmergames.bukkit.towny.permissions.TownyPerms;
import com.palmergames.bukkit.util.BukkitTools;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


/**
 * @author ElgarL
 *
 */
public class OnPlayerLogin implements Runnable {
	
	Towny plugin;
	com.palmergames.bukkit.towny.TownyUniverse universe;
	volatile Player player;
	
	/**
	 * Constructor
	 * 
	 * @param plugin - Towny plugin,
	 * @param player - Player to run login code on.
	 */
	public OnPlayerLogin(Towny plugin, Player player) {
		
		this.plugin = plugin;
		this.universe = com.palmergames.bukkit.towny.TownyUniverse.getInstance();
		this.player = player;
	}

	@Override
	public void run() {
		
		Resident resident = null;

		// A player returning a v3 UUID means the server is in true offline mode and not behind a bungee proxy. 
		if (TownyTimerHandler.isGatherResidentUUIDTaskRunning() && player.getUniqueId().version() == 3)
			GatherResidentUUIDTask.markOfflineMode();

		
		if (!universe.getResidentUUIDNameMap().containsKey(player.getUniqueId())) {
			/*
			 * No record of this resident's UUID.
			 */
			try {
				// If the universe has a resident and the resident has no UUID, log them in with their current name.
				if (universe.getDataSource().hasResident(player.getName()) && !universe.getDataSource().getResident(player.getName()).hasUUID()) {
					loginExistingResident(universe.getDataSource().getResident(player.getName()));
					
				// Else we're dealing with a new resident, because there's no resident by that UUID or resident by that Name without a UUID.
				} else {

					/*
					 * Make a brand new Resident.
					 */
					try {
						universe.getDataSource().newResident(player.getName());
						resident = universe.getDataSource().getResident(player.getName());
						
						if (TownySettings.isShowingRegistrationMessage())				
							TownyMessaging.sendMessage(player, Translation.of("msg_registration", player.getName()));
						resident.setRegistered(System.currentTimeMillis());
						resident.setLastOnline(System.currentTimeMillis());
						resident.setUUID(player.getUniqueId());
						TownySettings.incrementUUIDCount();
						if (!TownySettings.getDefaultTownName().equals("")) {
							Town town = TownyUniverse.getInstance().getTown(TownySettings.getDefaultTownName());
							if (town != null) {
								try {
									resident.setTown(town);
									universe.getDataSource().saveTown(town);
								} catch (AlreadyRegisteredException ignore) {}
							}
						}
						
						universe.getDataSource().saveResident(resident);
						
					} catch (AlreadyRegisteredException | NotRegisteredException ignored) {}

				}
			} catch (NotRegisteredException ignored) {}
			
		} else {
			/*
			 * We do have record of this UUID being used before, log in the resident after checking for a name change.
			 */
			String oldname = universe.getResidentUUIDNameMap().get(player.getUniqueId());
			try {
				resident = universe.getDataSource().getResident(oldname);
			} catch (NotRegisteredException ignored) {}
			
			// Name change test.
			if (!resident.getName().equals(player.getName())) {
				try {
					universe.getDataSource().renamePlayer(resident, player.getName());
				} catch (AlreadyRegisteredException e) {
					e.printStackTrace();
				} catch (NotRegisteredException e) {
					e.printStackTrace();
				}
			}
			/*
			 * This resident is known so fetch the data and update it.
			 */
			try {
				resident = universe.getDataSource().getResident(player.getName());
				loginExistingResident(resident);
				
			} catch (NotRegisteredException ignored) {}
		}

		if (resident != null) {
			TownyPerms.assignPermissions(resident, player);
				
			try {
				if (TownySettings.getShowTownBoardOnLogin() && resident.hasTown() &&  !resident.getTown().getBoard().isEmpty())
					TownyMessaging.sendTownBoard(player, resident.getTown());

				if (TownySettings.getShowNationBoardOnLogin() && resident.getTown().hasNation() && !resident.getTown().getNation().getBoard().isEmpty())
					TownyMessaging.sendNationBoard(player, resident.getTown().getNation());

			} catch (NotRegisteredException ignored) {}
		
			if (TownyAPI.getInstance().isWarTime())
				universe.getWarEvent().sendScores(player, 3);
		
			//Schedule to setup default modes when the player has finished loading
			if (BukkitTools.scheduleSyncDelayedTask(new SetDefaultModes(player.getName(), false), 1) == -1)
				TownyMessaging.sendErrorMsg("Could not set default modes for " + player.getName() + ".");
			
			// Send any warning messages at login.
			warningMessage(resident);
		}
	}
	
	/**
	 * Update last online, add UUID if needed, then save the resident.
	 * 
	 * @param resident Resident logging in.
	 */
	private void loginExistingResident(Resident resident) {
		if (TownySettings.isUsingEssentials()) {
			Essentials ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
			/*
			 * Don't update last online for a player who is vanished.
			 */
			if (!ess.getUser(player).isVanished())
				resident.setLastOnline(System.currentTimeMillis());
		} else {
			resident.setLastOnline(System.currentTimeMillis());
		}
		if (!resident.hasUUID()) {
			resident.setUUID(player.getUniqueId());
			TownySettings.incrementUUIDCount();
		}
		universe.getDataSource().saveResident(resident);
			
	}
	
	/**
	 * Send a warning message if the town or nation is due to be deleted.
	 * 
	 * @param resident - Resident to send the warning to.
	 */
	private void warningMessage(Resident resident) {

		if (TownyEconomyHandler.isActive() && TownySettings.isTaxingDaily()) {
			if (resident.hasTown()) {
				try {
					Town town = resident.getTown();
					if (town.hasUpkeep()) {
						double upkeep = TownySettings.getTownUpkeepCost(town);
						try {
							if ((upkeep > 0) && (!town.getAccount().canPayFromHoldings(upkeep))) {
								/*
								 *  Warn that the town is due to be deleted/bankrupted.
								 */
								if(TownySettings.isTownBankruptcyEnabled()) {
									if (!town.getAccount().isBankrupt()) //Is town already bankrupt?
										TownyMessaging.sendMessage(resident, Translation.of("msg_warning_bankrupt", town.getName()));
								} else {
									TownyMessaging.sendMessage(resident, Translation.of("msg_warning_delete", town.getName()));
								}
							}
						} catch (EconomyException ex) {
							// Economy error, so ignore it and try to continue.
						}
					}
						
					if (town.hasNation()) {
						Nation nation = town.getNation();
						
						double upkeep = TownySettings.getNationUpkeepCost(nation);
						try {
							if ((upkeep > 0) && (!nation.getAccount().canPayFromHoldings(upkeep))) {
								/*
								 *  Warn that the nation is due to be deleted.
								 */
								TownyMessaging.sendMessage(resident, Translation.of("msg_warning_delete", nation.getName()));
							}
						} catch (EconomyException ex) {
							// Economy error, so ignore it and try to continue.
						}
					}
					
				} catch (NotRegisteredException ex) {
					// Should never reach here as we tested it beforehand.
				}
			}
		}
		
	}
}
