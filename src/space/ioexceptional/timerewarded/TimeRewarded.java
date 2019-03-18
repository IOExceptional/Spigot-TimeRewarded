package space.ioexceptional.timerewarded;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class TimeRewarded implements CommandExecutor {
	static ConfigWrapper TimeRewardedConfig;
	static ConfigWrapper TimeRewardedData;
	
	private BukkitTask _task;
	private Main _plugin;
	private Server _server;
	
	// Configuration + defaults
	private int _seconds = 30;
	private List<String> _blacklistedPlayers = new ArrayList<String>();
	private List<TimeReward> _timeRewards = new ArrayList<TimeReward>();
	private HashMap<String, Integer> _playerRewardTiers = new HashMap<String, Integer>();
	
	public TimeRewarded(Main instance) {
		_plugin = instance;
		
		_server = instance.getServer();
		
		getConfig();
		loadConfig();
	}

	public void enable() {
		_task = _server.getScheduler().runTaskTimer(_plugin, new Runnable() {
			@Override
			public void run() {
				checkPlayers();
			}
		}, 20 * _seconds, 20 * _seconds);
	}
	
	public void disable() {
		_task.cancel();
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		Player playerInQuestion = null;
		
		if (sender instanceof Player && args.length == 0) {
			playerInQuestion = (Player)sender;			
		} else {
			if (args.length == 0) {			
				return false;
			}
			
			if (!sender.hasPermission("timerewarded.playtime.others")) {
				return false;
			}
			
			playerInQuestion = getPlayerByString(args[0]);
		}
		
		if (playerInQuestion == null) {
			return false;
		}
		
		int playtime = getPlayerPlaytime(playerInQuestion);
		
		String formattedTime = "";
		
		if (playtime > 60) {
			formattedTime += (int)(playtime / 60) + " hour(s), ";
		}
		
		formattedTime += (int)(playtime % 60) + " minute(s)";
		
		sender.sendMessage(playerInQuestion.getName() + " has played: " + formattedTime);
		
		return true;
	}
	
	public void reload() {
		loadConfig();
	}
	
	private void getConfig() {
		_blacklistedPlayers.add("Notch");
		
		TimeRewarded.TimeRewardedConfig = new ConfigWrapper(_plugin, null, "config.yml");
		TimeRewarded.TimeRewardedData = new ConfigWrapper(_plugin, null, "data.yml");
		
		 FileConfiguration configFile = TimeRewardedConfig.getConfig();
		 
		 configFile.addDefault("timeout", _seconds);
		 configFile.addDefault("blacklist", _blacklistedPlayers);
		 
		 configFile.options().copyDefaults(true);
		 TimeRewardedConfig.saveConfig();
		 TimeRewardedConfig.reloadConfig();
	}
	
	private void loadConfig() {
		FileConfiguration config = TimeRewarded.TimeRewardedConfig.getConfig();
		 
		 _seconds = config.getInt("timeout");
		 _blacklistedPlayers = config.getStringList("blacklist");
		 
		 readTimers(config);
	}

	private void readTimers(FileConfiguration config) {
		
		ConfigurationSection section = (ConfigurationSection) config.get("timers");
		Set<String> keys = section.getKeys(false);
		for(String key : keys) {
			try {
				TimeReward timeReward = new TimeReward();			
			
				timeReward.name = key;
				timeReward.enabled = config.getBoolean("timers." + key + ".enabled");
				timeReward.time = config.getInt("timers." + key + ".time");
				timeReward.commands = (List<String>) config.getList("timers." + key + ".commands");
				
				_timeRewards.add(timeReward);
			} catch (Exception e) {
				_server.getLogger().warning("Couldn't load timer: " + key);
			}
		}
		 
		 _server.getLogger().info("Loaded " + _timeRewards.size() + " time rewards");
	}
	
	private void getData() {
		TimeRewarded.TimeRewardedData = new ConfigWrapper(_plugin, null, "playerdata.yml");
		
		 FileConfiguration configFile = TimeRewardedData.getConfig();
		 
		 configFile.options().copyDefaults(true);
		 TimeRewardedData.saveConfig();
		 TimeRewardedData.reloadConfig();
	}
	
	private void checkPlayers() {
		Collection<? extends Player> onlinePlayers = _server.getOnlinePlayers();
		
		if (onlinePlayers == null || onlinePlayers.size() == 0) {
			return;
		}
		
		FileConfiguration dataFile = TimeRewardedData.getConfig();
		
		
		for(Player plr : onlinePlayers) {			
			String name = plr.getName();
			String uniqueId = plr.getUniqueId().toString();
			
			if (_blacklistedPlayers.contains(name) || _blacklistedPlayers.contains(uniqueId)) {				
				continue;
			}
			
			int playtime = getPlayerPlaytime(plr);
			int previousRewardTime = 0;
			
			if (dataFile.contains(uniqueId)) {
				previousRewardTime = dataFile.getInt(uniqueId);
			}
			
			for(TimeReward tr : _timeRewards) {
				if (!tr.enabled) {
					continue;
				}
				
				if (previousRewardTime >= tr.time || playtime < tr.time) {
					continue;
				}
				
				_server.getLogger().info(plr.getName() + " rewarded with " + tr.name + ". Playtime: " + playtime + ", Previous Reward time: " + previousRewardTime);
				
				runTimeRewardCommands(tr, plr);
				
				dataFile.set(uniqueId, tr.time);
			}
		}
	
		TimeRewardedData.saveConfig();
		TimeRewardedData.reloadConfig();
	}

	private void runTimeRewardCommands(TimeReward tr, Player plr) {
		for (String command : tr.commands) {
			command = command.replace("<player>", plr.getName());
			
			try {
				_server.getLogger().info("Running command: " + command);
				_server.dispatchCommand(_server.getConsoleSender(), command);
			} catch(Exception e) {
				_server.getLogger().warning("Couldn't run command: " + command);
			}
		}
	}

	private Player getPlayerByString(String playerNameOrUniqueId) {
		Player plr = _server.getPlayerExact(playerNameOrUniqueId);
		
		if (plr == null) {
			plr = _server.getPlayer(UUID.fromString(playerNameOrUniqueId));
		}
		
		return plr;
	}
	
	private int getPlayerPlaytime(Player plr) {
		return (plr.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20) / 60;
	}
}
