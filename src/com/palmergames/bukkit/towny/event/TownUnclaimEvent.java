package com.palmergames.bukkit.towny.event;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * This event is no longer fired by Towny and is marked for future removal.
 *
 * @deprecated As of 0.96.5.5, please use {@link com.palmergames.bukkit.towny.event.town.TownUnclaimEvent}.
 */
@Deprecated
public class TownUnclaimEvent extends Event  {

    private static final HandlerList handlers = new HandlerList();
    
    private final Town town;
    private final WorldCoord worldCoord;

    @Override
    public HandlerList getHandlers() {
    	
        return handlers;
    }
    
    public static HandlerList getHandlerList() {

		return handlers;
	}

    public TownUnclaimEvent(Town _town, WorldCoord _worldcoord) {
        super(!Bukkit.getServer().isPrimaryThread());
        this.town = _town;
        this.worldCoord = _worldcoord;
    }

    /**
     *
     * @return the Town.
     */
    public Town getTown() {
        return town;
    }
    
    /**
    *
    * @return the Unclaimed WorldCoord.
    *
    */
   public WorldCoord getWorldCoord() {
       return worldCoord;
   }
    
}