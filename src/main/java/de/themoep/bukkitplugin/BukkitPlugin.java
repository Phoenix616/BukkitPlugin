package de.themoep.bukkitplugin;
/*
 * BukkitPlugin
 * Copyright (c) 2024 Max Lee aka Phoenix616 (max@themoep.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.google.common.base.Charsets;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.apache.commons.lang.WordUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

public abstract class BukkitPlugin extends JavaPlugin {

	private Timer timer;
	private boolean enableCalled = false;
	private static final String DISPLAY_TYPE_KEY = "bukkitplugin.info.display";
	private static final String INFORM_TYPE_KEY = "bukkitplugin.command.shownoninform";

	private LicenseInfo licenseInfo;
	private String commandAlias;

	private boolean debug = true;
	private Map<Plugin, LicenseInfo> cachedInfos = null;

	public BukkitPlugin() {
		super();
		init();
	}

	protected BukkitPlugin(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, final File file) {
		super(loader, description, dataFolder, file);
		init();
	}

	private void init() {
		commandAlias = getName().toLowerCase(Locale.ROOT);

		timer = new Timer();
		// Schedule task to run five minutes in the future to check enable status
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (BukkitPlugin.this.isEnabled() && !enableCalled) {
					getServer().getScheduler().runTask(BukkitPlugin.this, () -> {
						getLogger().severe(getName() + " was not enabled properly! Contact the plugin author " + String.join(", ", getDescription().getAuthors()) + "! Disabling it...");
						getServer().getPluginManager().disablePlugin(BukkitPlugin.this);
					});
				}
			}
		}, 1000 * 60 * 5);

		licenseInfo = createLicenseInfo();
	}

	private LicenseInfo createLicenseInfo() {
		LicenseInfo licenseInfo = createLicenseInfo(this);
		if (licenseInfo == null) {
			licenseInfo = new LicenseInfo();
		}
		return licenseInfo;
	}

	private static LicenseInfo createLicenseInfo(Plugin plugin) {
		InputStream descriptionStream = plugin.getResource("plugin.yml");
		if (descriptionStream != null) {
			YamlConfiguration descriptionConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(descriptionStream, Charsets.UTF_8));

			return new LicenseInfo(
					descriptionConfig.getBoolean("inform-user"),
					descriptionConfig.getString("license"),
					descriptionConfig.getString("license-terms"),
					descriptionConfig.getString("source")
			);
		}
		return null;
	}

	@Override
	public void onEnable() {
		enableCalled = true;
		loadOwnConfig();
		getServer().getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onPluginDisable(PluginDisableEvent event) {
				if (event.getPlugin() == BukkitPlugin.this) {
					if (timer != null) {
						timer.cancel();
					}
				}
			}
		}, this);
		PluginCommand command = getCommand(getName().toLowerCase(Locale.ROOT));
		if (getLicense() != null) {
			getLogger().info("This plugin is licensed under the terms of the " + getLicense() + ".");
		}
		if (command != null && command.getPlugin() == this) {
			command.setExecutor(this);
			commandAlias = command.getName();
			for (String alias : command.getAliases()) {
				if (alias.length() < commandAlias.length()) {
					commandAlias = alias;
				}
			}
			if (getLicenseTerms() != null || getSourceLink() != null) {
				if (getLicenseTerms() != null) {
					getLogger().info("More info about the plugin like the license" + (getSourceLink() != null ? " and the source" : "") + ": /" + command.getName() + " info");
				} else {
					getLogger().info("More info about the plugin like the source: /" + command.getName() + " info");
				}
			}
			if (shouldInformUser()) {
				getServer().getPluginManager().registerEvents(new InfoListener(), this);
				if (command.getPermission() != null) {
					Permission permission = getServer().getPluginManager().getPermission(command.getPermission());
					if (permission == null) {
						getServer().getPluginManager().addPermission(new Permission(command.getPermission(), PermissionDefault.TRUE));
					} else if (!permission.getDefault().getValue(false)) {
						getLogger().warning("Potential error in permission default for command permission '" + permission.getName() + "'!" +
								"Normal players need to access that plugin command to comply with the license requirements. Please make sure to grant it to them!");
					}
				}
				PluginCommand licenseCommand = getServer().getPluginCommand("licenses");
				if (licenseCommand == null || !(licenseCommand.getPlugin() instanceof BukkitPlugin)) {
					getCommandMap().register(commandAlias, new LicenseCommand());
				}
			}
		} else {
			if (getLicenseTerms() != null) {
				for (String line : getLicenseTerms().split("\n")) {
					getLogger().info(line);
				}
			}
			if (getSourceLink() != null) {
				getLogger().info("The source is available at " + getSourceLink());
			}
			if (shouldInformUser()) {
				getLogger().severe("Unable to register plugin command as it was not defined in the plugin.yml?");
				getLogger().severe("Plugin requires that the user has access to more information so the info command is required!");
				getServer().getScheduler().runTaskLater(this, () -> getServer().getPluginManager().disablePlugin(this), 1);
				licenseInfo.shouldInformUser(false);
			}
		}
	}

	private CommandMap getCommandMap() {
		try {
			return (CommandMap) getServer().getClass().getDeclaredMethod("getCommandMap").invoke(getServer());
		} catch (Exception e) {
			Field commandMap = null;
			try {
				commandMap = getServer().getClass().getField("commandMap");
				commandMap.setAccessible(true);
				return (CommandMap) commandMap.get(getServer());
			} catch (NoSuchFieldException | IllegalAccessException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (args.length == 0) {
			return false;
		}
		if (shouldInformUser() || sender.hasPermission(getName().toLowerCase(Locale.ROOT) + ".command.info")) {
			if ("info".equalsIgnoreCase(args[0]) || "license".equalsIgnoreCase(args[0])) {
				for (Map.Entry<String, String> entry : getPluginInfo().entrySet()) {
					if (entry.getValue() != null && !entry.getValue().isEmpty()) {
						String key = WordUtils.capitalizeFully(entry.getKey().replace('-', ' '));
						if (entry.getValue().split("\n").length == 1) {
							sender.sendMessage(ChatColor.GRAY + key + ": " + ChatColor.WHITE + entry.getValue());
						} else {
							sender.sendMessage(ChatColor.GRAY + key + ":\n" + ChatColor.WHITE + entry.getValue());
						}
					}
				}
				return true;
			}
		}

		if ("reload".equalsIgnoreCase(args[0]) && sender.hasPermission(getName().toLowerCase(Locale.ROOT) + ".command.reload")) {
			if (loadOwnConfig()) {
				sender.sendMessage(ChatColor.GREEN + getName() + " config successfully reloaded!");
			} else {
				sender.sendMessage(ChatColor.YELLOW + "Error while reloading " + getName() + "!");
			}
			return true;
		}
		return false;
	}

	/**
	 * Whether the usage of this plugin and it's terms should be sent to every user of the server (e.g. AGPL requires that)
	 * @return Whether the usage of this plugin and it's terms should be sent to every user of the server
	 */
	public boolean shouldInformUser() {
		return licenseInfo.shouldInformUser();
	}

	/**
	 * Get the name of the license that this plugin is using
	 * @return The name of the license
	 */
	public String getLicense() {
		return licenseInfo.getLicense();
	}

	/**
	 * Get the display terms of this license (most likely some short form pointing to the full one)
	 * @return The display terms of the license
	 */
	public String getLicenseTerms() {
		return licenseInfo.getLicenseTerms();
	}

	/**
	 * Get the link to the source code of the plugin binary
	 * @return Get the link to the source code of the plugin binary
	 */
	public String getSourceLink() {
		return licenseInfo.getSourceLink();
	}

	private Map<String, String> getPluginInfo() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("name", getName());
		map.put("provides", String.join(", ", getDescription().getProvides()));
		map.put("version", getDescription().getVersion());
		map.put("authors", String.join(", ", getDescription().getAuthors()));
		map.put("contributors", String.join(", ", getDescription().getContributors()));
		map.put("description", getDescription().getDescription());
		map.put("website", getDescription().getWebsite());
		map.put("source", getSourceLink());
		map.put("license", getLicense());
		map.put("license-terms", getLicenseTerms());
		return map;
	}

	public class InfoListener implements Listener {

		@EventHandler
		public void onPlayerJoin(PlayerJoinEvent event) {
			BaseComponent[] loginMessage = new ComponentBuilder().append(String.valueOf(getLicenseInfos().size())).color(ChatColor.GRAY)
					.append(" plugins found with license information: ").color(ChatColor.DARK_GRAY)
					.append("/licenses").color(ChatColor.GRAY)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/licenses")).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							new Text(new ComponentBuilder("Click to get more info about the licenses").color(ChatColor.GRAY).create())))
					.create();
			if ("chat".equals(System.getProperty(DISPLAY_TYPE_KEY))) {
				event.getPlayer().spigot().sendMessage(loginMessage);
			} else {
				event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, loginMessage);
			}
		}
	}

	private class LicenseCommand extends Command {

		public LicenseCommand() {
			super("licenses", "Get the license of the plugin", "/<command>", Collections.emptyList());
		}

		@Override
		public boolean execute(CommandSender sender, String commandLabel, String[] args) {
			List<BaseComponent[]> messages = new ArrayList<>();
			for (Map.Entry<Plugin, LicenseInfo> entry : getLicenseInfos().entrySet()) {
				Plugin plugin = entry.getKey();
				LicenseInfo licenseInfo = entry.getValue();
				ComponentBuilder builder = new ComponentBuilder("- ").color(ChatColor.GRAY)
						.append(plugin.getName()).color(ChatColor.WHITE)
						.append(": ").color(ChatColor.GRAY)
						.append(licenseInfo.getLicense()).color(ChatColor.WHITE);
				if (plugin instanceof BukkitPlugin) {
					builder.append(" | More info: ").color(ChatColor.GRAY)
							.append("/" + ((BukkitPlugin) plugin).commandAlias + " info").color(ChatColor.WHITE)
							.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ((BukkitPlugin) plugin).commandAlias + " info"))
							.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
									new Text(new ComponentBuilder("Click to get more info about " + plugin.getName()).color(ChatColor.GRAY).create())));
				}
				messages.add(builder.create());
			}
			if (messages.isEmpty()) {
				sender.sendMessage(ChatColor.RED + "No plugins with licenses found!");
			} else {
				sender.sendMessage(ChatColor.YELLOW + "Licenses of plugins on this server:");
				for (BaseComponent[] message : messages) {
					sender.spigot().sendMessage(message);
				}
			}
			return true;
		}
	}

	private Map<Plugin, LicenseInfo> getLicenseInfos() {
		if (cachedInfos != null) {
			return cachedInfos;
		}
		Map<Plugin, LicenseInfo> licenseInfos = new LinkedHashMap<>();
		for (Plugin plugin : getServer().getPluginManager().getPlugins()) {
			LicenseInfo licenseInfo = createLicenseInfo(plugin);
			if (licenseInfo != null && licenseInfo.getLicense() != null
					&& (licenseInfo.shouldInformUser() || "true".equals(System.getProperty(INFORM_TYPE_KEY, "true")))) {
				licenseInfos.put(plugin, licenseInfo);
			}
		}
		cachedInfos = licenseInfos;
		return licenseInfos;
	}

	/**
	 * Log a debug message
	 * @param message The message to log
	 */
	public void logDebug(String message) {
		logDebug(message, null);
	}

	/**
	 * Log a debug message
	 * @param message   The message to log
	 * @param throwable A throwable which's stack to print too
	 */
	public void logDebug(String message, Throwable throwable) {
		if (debug) {
			getLogger().log(Level.INFO, "[DEBUG] " + message, throwable);
		}
	}

	private boolean loadOwnConfig() {
		saveDefaultConfig();
		reloadConfig();
		debug = getConfig().getBoolean("debug", debug);
		return loadConfig();
	}

	/**
	 * Run a task synchronously on the main thread.
	 * If the current thread is the main thread, the task will be run immediately
	 * @param runnable The task to run
	 * @return The task that was scheduled or <code>null</code> if it was run immediately
	 */
	public BukkitTask runSync(Runnable runnable) {
		if (getServer().isPrimaryThread()) {
			runnable.run();
			return null;
		}
		return getServer().getScheduler().runTask(this, runnable);
	}

	/**
	 * Run a task asynchronously on another thread
	 * @param runnable The task to run
	 * @return The task that was scheduled
	 */
	public BukkitTask runAsync(Runnable runnable) {
		return getServer().getScheduler().runTaskAsynchronously(this, runnable);
	}

	/**
	 * Load values from the config
	 * @return Whether the config was successfully loaded
	 */
	public abstract boolean loadConfig();

	private static class LicenseInfo {
		private boolean informUser;
		private final String license;
		private final String licenseTerms;
		private final String sourceLink;

		public LicenseInfo() {
			this.informUser = false;
			this.license = null;
			this.licenseTerms = null;
			this.sourceLink = null;
		}

		public LicenseInfo(boolean informUser, String license, String licenseTerms, String source) {
			this.informUser = informUser;
			this.license = license;
			this.licenseTerms = licenseTerms;
			this.sourceLink = source;
		}

		public boolean shouldInformUser() {
			return informUser;
		}

		public String getLicense() {
			return license;
		}

		public String getLicenseTerms() {
			return licenseTerms;
		}

		public String getSourceLink() {
			return sourceLink;
		}

		public void shouldInformUser(boolean informUser) {
			this.informUser = informUser;
		}
	}
}