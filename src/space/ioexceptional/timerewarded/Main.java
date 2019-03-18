package space.ioexceptional.timerewarded;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
	
	TimeRewarded _timeRewarded;

	public Main() {
		super();
		
		_timeRewarded = new TimeRewarded(this);
	}
	
	@Override
	public void onEnable() {
		_timeRewarded.enable();
		getCommand("playtime").setExecutor(_timeRewarded);
	}
	
	@Override
	public void onDisable() {
		_timeRewarded.disable();
	}
}
