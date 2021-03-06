package me.dbstudios.dayjobs;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;
import org.bukkit.plugin.Plugin;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;


public class DayJobs extends JavaPlugin {
	// Private variables
	public String pluginDir = "plugins" + File.separator + "dbstudios" + File.separator + "DayJobs" + File.separator;
	private Logger log = Logger.getLogger("Minecraft");
	private String version = "1.2";
	private static PermissionHandler PermHandler;
	public String prefix = "<DayJobs> ";
	private Boolean debug = false;
	private Boolean usePerms = false;
		
	// Configuration links
	private Configuration config = new Configuration(new File(pluginDir + "config.yml"));
	private Configuration players = new Configuration(new File(pluginDir + "player.yml"));
	private Configuration ticket = new Configuration(new File(pluginDir + "ticket.yml"));
	private Configuration zones = new Configuration(new File(pluginDir + "zones.yml"));
	
	//Listeners
	private final DayJobsBlockListener blockListener = new DayJobsBlockListener(this);
	private final DayJobsPlayerListener playerListener = new DayJobsPlayerListener(this);
	private final DayJobsInventoryListener inventoryListener = new DayJobsInventoryListener(this);
	
	@Override
	public void onEnable() {
		// Register listeners
	 	PluginManager manager = this.getServer().getPluginManager();
		manager.registerEvent(Event.Type.BLOCK_PLACE, blockListener, Event.Priority.Normal, this);
		manager.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Event.Priority.Normal, this);
		manager.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Event.Priority.Normal, this);
		manager.registerEvent(Event.Type.PLAYER_PICKUP_ITEM, playerListener, Event.Priority.Normal, this);
		manager.registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Event.Priority.Normal, this);
		manager.registerEvent(Event.Type.PLAYER_MOVE, playerListener, Event.Priority.Normal, this);
		
		if (setupSpout()) {
			manager.registerEvent(Event.Type.CUSTOM_EVENT, inventoryListener, Event.Priority.Normal, this);
		}
		
		//Initialize Permissions
		setupPerms();
						
		config.load();
		players.load();
		ticket.load();
		zones.load();
		
		if (config.getString("config.enabled") == "false") {
			log.info(prefix + "Plugin disabled (Reason: config.enabled set to 'false').");
		}
		
		if (config.getString("config.debug") == "true") {
			log.info(prefix + "Verbose logging enabled.");
			debug = true;
		}
		
		log.info(prefix + "Plugin version " + version + " enabled.");
	}
	
	@Override
	public void onDisable() {
		log.info(prefix + "Plugin disabled.");
	}
	
	public void setupPerms() {
		if (PermHandler != null) {
			return;
		}
		
		Plugin PermPlug = this.getServer().getPluginManager().getPlugin("Permissions");
		
		if (PermPlug == null) {
			log.info("Permissions not found, defaulting to OP.");
			usePerms = false;
			return;
		}
		
		PermHandler = ((Permissions)PermPlug).getHandler();
		log.info(prefix + "Permissions found, using " + ((Permissions)PermPlug).getDescription().getFullName());
		usePerms = true;
	}
	
	public Boolean setupSpout() {
		if (this.getServer().getPluginManager().getPlugin("Spout") == null) {
			log.info("Spout not found! Many features will NOT be available.");
			return false;
		} else {
			return true;
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		return (new DayJobsCommands(this)).tryCommand(sender, cmd, label, args);
	}
	
	/* *** COMMON *** */
	public Boolean checkMatch(String block, String player, String type) {
		/**
		 * 	block	- The block (or item) in hand/being placed
		 * 	player	- the player to perform the check on
		 * 	type	- the type of check to perform (i.e. 'can-place', 'can-wear', 'can-use')
		 * 
		 * So, for example, to check if player "LartTyler" can place the block "DIRT", one would call:
		 * 	checkMatch("DIRT", this.getPlayer("LartTyler"), "can-place");
		 */
		
		Boolean matched = false;
		block = block.replace("[", "").replace("]", "");
		
		String job = getJob(player);
		String[] toCheck = parseToList(getConfig("jobs." + job + "." + type));
		
		for (String item : toCheck) {
			ifDebug("Checking '" + item + "' against '" + block + "'.");
			
			if (item.equalsIgnoreCase(block) || item.equalsIgnoreCase("ALL")) {
				matched = true;
				break;
			}
		}
		
		if (!matched) {
			toCheck = parseToList(getConfig("all." + type));
			for (String item : toCheck) {
				if (item.equalsIgnoreCase(block) || item.equalsIgnoreCase("ALL")) {
					matched = true;
					break;
				}
			}
		}
		
		ifDebug("CheckMatch returning '" + matched.toString() + "'.");
		return matched;
	}
	
	public String[] parseToList(String toParse) {
		return (toParse.replace("[", "").replace("]", "").replace(" ", "").split(","));
	}
	
	public List<String> getJobList() {
		/**
		 * Returns a List<String> of all jobs available to the player
		 */

		return config.getKeys("config.jobs");
	}

	public String getInfo(String job, String node) {
		/**
		 * Returns a job info node.
		 */
		
		String toReturn = config.getString("config.jobs." + job + "." + node);
		
		if (toReturn == null) {
			toReturn = "null";
		}

		return toReturn;
	}

	public Boolean createTicket(String subject, String time, String job) {
		/**
		 * Attempts to create a new ticket in pluginDir + "ticket.yml"
		 * Will return true on success, false if the write failed
		 */

		String base = "ticket." + subject;

		ticket.setProperty(base + ".time", time);
		ticket.setProperty(base + ".job", job);
		ticket.save();

		return (getTicket(subject, "job") == job);
	}

	public String getJob(String player) {
		/**
		 * Returns <player>'s current job class
		 */

		return players.getString("player." + player + ".job");
	}

	public String parseToLine(String toParse) {
		/**
		 * Parses a YAML value list to a more visually appealing single line list
		 */

		return (toParse.replace("[", "").replace("]", "").replace(",", ", ").replace("_", " "));
	}

	public Boolean hasPerm(String player, String perm) {
		/**
		 * Returns true if <player> has the correct <perm> node, false if not
		 */
		perm = "dbstudios.dayjobs." + perm;
		Boolean hasPerm = false;
		
		if (usePerms) {
			hasPerm = PermHandler.has(this.getServer().getPlayer(player), perm);
		} else {
			hasPerm = this.getServer().getPlayer(player).isOp();
		}
		
		return hasPerm;
	}

	public void reloadConf() {
		/**
		 * Attempt to reload all configuration files
		 */

		config.load();
		ticket.load();
		players.load();
		zones.load();
	}

	public List<String> getOpenTickets() {
		/**
		 * Return a List<String> of all open tickets
		 */

		return (ticket.getKeys("ticket"));
	}

	public String getTicket(String subject, String node) {
		/**
		 * Attempts to get the specified <node> from the ticket made by <subject>
		 */

		return ticket.getString("ticket." + subject + "." + node);
	}

	public Boolean playerExists(String player) {
		/**
		 * Return true if <player>'s job is found, false if not (he doesn't exist)
		 */

		return (getJob(player) != null);
	}

	public Boolean jobExists(String job) {
		/**
		 * Return true if <job> can be found in config.yml, false if not
		 */

		return (config.getString("config.jobs." + job + ".friendly-name") != null);
	}

	public Boolean changeJob(String player, String job) {
		/**
		 * Attempts to change <player>'s job to <job>
		 * Will return true on success, false otherwise
		 */

		if (playerExists(player) && jobExists(job)) {
			players.setProperty("player." + player + ".job", job);
			players.save();

			return (getJob(player) == job);
		} else {
			return false;
		}
	}

	public Boolean ticketExists(String subject) {
		/**
		 * Return true if a ticket has been opened by <subject>
		 */

		return (getTicket(subject, "time") != null);
	}

	public Boolean closeTicket(String subject) {
		/**
		 * Attempts to close ticket opened by <subject>
		 */

		if (ticketExists(subject)) {
			ticket.removeProperty("ticket." + subject);
			ticket.save();
			return (ticket.getProperty("ticket." + subject) == null);
		} else {
			return false;
		}
	}
	
	public String getConfig(String opt) {
		/**
		 * Load <opt> from config.yml
		 */
		String toReturn = config.getString("config." + opt);
		
		if (toReturn == null) {
			toReturn = "null";
		}
		
		return toReturn;
	}
	
	public Boolean writeConfig(String opt, String val) {
		/**
		 * Attempt to write <opt>: <val> to config.yml
		 * Will return true on success, else returns false
		 */
		
		config.setProperty("config." + opt, val);
		config.save();
		
		reloadConf();
		
		return (config.getString("config." + opt) == val);
	}
	
	public Boolean createPlayer(String player, String job) {
		/**
		 * Attempt to create <player> and add them into player.yml
		 */
		
		players.setProperty("player." + player + ".job", job);
		players.save();
		
		return (getJob(player) != null);
	}
	
	public void ifDebug(String msg) {
		/**
		 * Output additional debug info to the console if the correct configuration node is set
		 */
		
		if (debug) {
			log.info(prefix + msg);
		}
	}
	
	public Boolean toggleDebug() {
		/**
		 * Toggle debug mode
		 */
		
		debug = !debug;	
		return true;
	}
	
	public Boolean createZone(String name, Integer[] upper, Integer[] lower, String order, String acl) {
		/**
		 * Attempt to create a new zone, <name>, from the given variables
		 */
		
		String base = "zones." + name + ".";
		String[] tmp = order.split(",");
		String fromType = tmp[1];
		
		zones.setProperty(base + "order", order);
		zones.setProperty(base + fromType + "-from", acl);
		zones.setProperty(base + "deny-message", "A mysterious force pushes you away...");
		zones.setProperty(base + "coords.upper.x", upper[0]);
		zones.setProperty(base + "coords.upper.y", upper[1]);
		zones.setProperty(base + "coords.upper.z", upper[2]);
		zones.setProperty(base + "coords.lower.x", lower[0]);
		zones.setProperty(base + "coords.lower.y", lower[1]);
		zones.setProperty(base + "coords.lower.z", lower[2]);
		
		zones.save();
		
		return (zoneExists(name));
	}
	
	public Boolean zoneExists(String zone) {
		/**
		 * Return true if <zone> can be found (by checking if zones.<zone>.order is null
		 */
		
		return (zones.getString("zones." + zone + ".order") != null);		
	}
	
	public Boolean deleteZone(String zone) {
		/**
		 * Attempt to delete <zone>
		 */
		
		zones.removeProperty("zones." + zone);
		zones.save();
		reloadConf();
		return (!zoneExists(zone));
	}
	
	public List<String> getZones() {
		/**
		 * Return a List<String> of all available zones
		 */
		
		return (zones.getKeys("zones"));
	}
	
	public String getZoneInfo(String zone, String path) {
		/**
		 * Return the configuration on <zone> at the given <path>
		 */
		
		return (zones.getString("zones." + zone + "." + path));
	}
	
	public Boolean isWithin(String player, String zone) {
		/**
		 * Returns true if <player> is within <zone>
		 */
		
		Integer[] locat = getPlayerLocation(player);
		Integer[] upper = getZoneUpper(zone);
		Integer[] lower = getZoneLower(zone);
		Boolean isWithin;
		
		if (lower[0] <= locat[0] && lower[1] <= locat[1] && lower[2] <= locat[2] &&
				upper[0] >= locat[0] && upper[1] >= locat[1] && upper[2] >= locat[2]) {
			isWithin = true;			
		} else {
			isWithin = false;
		}
		
		return isWithin;	
	}
	
	public Integer[] getZoneUpper(String zone) {
		/**
		 * Returns the upper left coordinate that defines <zone>
		 */
		
		Integer[] upper = {Integer.parseInt(getZoneInfo(zone, "coords.upper.x")),
				Integer.parseInt(getZoneInfo(zone, "coords.upper.y")),
				Integer.parseInt(getZoneInfo(zone, "coords.upper.z"))};
		
		return upper;
	}
	
	public Integer[] getZoneLower(String zone) {
		/**
		 * Returns the lower right coordinate that defines <zone>
		 */
		
		Integer[] lower = {Integer.parseInt(getZoneInfo(zone, "coords.lower.x")),
				Integer.parseInt(getZoneInfo(zone, "coords.lower.y")),
				Integer.parseInt(getZoneInfo(zone, "coords.lower.z"))};
		
		return lower;
	}
	
	public Integer[] getPlayerLocation(String player) {
		/**
		 * Returns an Integer array containing the block coordinates that <player> is currently located on
		 */
		
		Location playerLoc = this.getServer().getPlayer(player).getLocation();
		Integer[] locat = {playerLoc.getBlockX(),
				playerLoc.getBlockY(),
				playerLoc.getBlockZ()};
		
		return locat;
	}
	
	public int getDirection(Location locFrom, Location locTo) {
		int direction = -1;
				
		if (locFrom.getX() > locTo.getX() && locFrom.getBlockZ() == locTo.getBlockZ()) {
			direction = 1;
		} else if (locFrom.getZ() > locTo.getZ() && locFrom.getBlockX() == locTo.getBlockX()) {
			direction = 2;
		} else if (locFrom.getX() < locTo.getX() && locFrom.getBlockZ() == locTo.getBlockZ()) {
			direction = 3;
		} else if (locFrom.getZ() < locTo.getZ() && locFrom.getBlockX() == locTo.getBlockX()) {
			direction = 0;
		}
		
		return direction;
	}
}