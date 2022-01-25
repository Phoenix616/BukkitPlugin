package de.themoep.bukkitplugin;
/*
 * bukkitplugin
 * Copyright (c) 2022 Max Lee aka Phoenix616 (max@themoep.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.WordUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public abstract class BukkitPlugin extends JavaPlugin implements Listener {

    private Timer timer;
    private boolean enableCalled = false;
    private static final String DISPLAY_TYPE_KEY = "bukkitplugin.license.display";
    private static final String ACTIONBAR_DELAY_KEY = "bukkitplugin.license.actionbardelay";

    private boolean informUser = false;
    private String license = null;
    private String licenseTerms = null;
    private String sourceLink = null;
    private String commandAlias;
    private String loginMessage;

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

        InputStream descriptionStream = getResource("plugin.yml");
        if (descriptionStream != null) {
            YamlConfiguration descriptionConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(descriptionStream, Charsets.UTF_8));

            informUser = descriptionConfig.getBoolean("inform-user");
            license = descriptionConfig.getString("license");
            licenseTerms = descriptionConfig.getString("license-terms");
            sourceLink = descriptionConfig.getString("source");
        }
    }

    @Override
    public void onEnable() {
        enableCalled = true;
        saveDefaultConfig();
        reloadConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
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
            getLogger().info("More info about the plugin" + (getSourceLink() != null ? " like the source" : "") + ": /" + command.getName() + " info");
            if (shouldInformUser()) {
                getServer().getPluginManager().registerEvents(new InfoListener(), this);
                loginMessage = ChatColor.DARK_GRAY + "Using " + ChatColor.GRAY + getName() + ChatColor.DARK_GRAY
                        + (getLicense() != null ? " licensed under " + ChatColor.GRAY + getLicense() + ChatColor.DARK_GRAY : "")
                        + ". More info: " + ChatColor.GRAY + "/" + commandAlias + " info";
                if (command.getPermission() != null) {
                    Permission permission = getServer().getPluginManager().getPermission(command.getPermission());
                    if (permission == null) {
                        getServer().getPluginManager().addPermission(new Permission(command.getPermission(), PermissionDefault.TRUE));
                    } else if (!permission.getDefault().getValue(false)) {
                        getLogger().warning("Potentially permission default for command permission '" + permission.getName() + "'!" +
                                "Normal players need to access that plugin to comply with the license requirements. Please make sure to grant it to them!");
                    }
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
            getLogger().severe("Unable to register plugin command as it was not defined in the plugin.yml?");
            if (shouldInformUser()) {
                getLogger().severe("Plugin requires that the user has access to more information so the info command is required!");
                getServer().getScheduler().runTaskLater(this, () -> getServer().getPluginManager().disablePlugin(this), 1);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (shouldInformUser() || sender.hasPermission(getName().toLowerCase(Locale.ROOT) + ".command.info")) {
            if ("info".equalsIgnoreCase(args[0]) || "license".equalsIgnoreCase(args[0])) {
                for (Map.Entry<String, String> entry : getPluginInfo().entrySet()) {
                    if (entry.getValue() != null) {
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
            saveDefaultConfig();
            reloadConfig();
            if (loadConfig()) {
                sender.sendMessage(ChatColor.GREEN + getName() + " config successfully reloaded!");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Error while reloading " + getName() + "!");
            }
        }
        return false;
    }

    /**
     * Whether the usage of this plugin and it's terms should be sent to every user of the server (e.g. AGPL requires that)
     * @return Whether the usage of this plugin and it's terms should be sent to every user of the server
     */
    public boolean shouldInformUser() {
        return informUser;
    }

    /**
     * Get the name of the license that this plugin is using
     * @return The name of the license
     */
    public String getLicense() {
        return license;
    }

    /**
     * Get the display terms of this license (most likely some short form pointing to the full one)
     * @return The display terms of the license
     */
    public String getLicenseTerms() {
        return licenseTerms;
    }

    /**
     * Get the link to the source code of the plugin binary
     * @return Get the link to the source code of the plugin binary
     */
    public String getSourceLink() {
        return sourceLink;
    }

    private Map<String, String> getPluginInfo() {
        return ImmutableMap.of(
                "name", getName(),
                "provides", String.join(", ", getDescription().getProvides()),
                "version", getDescription().getVersion(),
                "authors", String.join(", ", getDescription().getAuthors()),
                "contributors", String.join(", ", getDescription().getContributors()),
                "description", getDescription().getDescription(),
                "website", getDescription().getWebsite(),
                "source", getSourceLink(),
                "license", getLicense(),
                "license-terms", getLicenseTerms()
        );
    }

    public class InfoListener implements Listener {

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            if ("chat".equals(System.getProperty(DISPLAY_TYPE_KEY))) {
                event.getPlayer().sendMessage(loginMessage);
            } else {
                int delay = Integer.getInteger(ACTIONBAR_DELAY_KEY + ":" + event.getPlayer().getName(), 1);
                System.setProperty(ACTIONBAR_DELAY_KEY + ":" + event.getPlayer().getName(), String.valueOf(delay + 1));
                UUID playerId = event.getPlayer().getUniqueId();
                getServer().getScheduler().runTaskLater(BukkitPlugin.this, () -> {
                    Player player = getServer().getPlayer(playerId);
                    if (player != null) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(loginMessage));
                    }
                }, delay * 3 * 20L);
            }
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            System.clearProperty(ACTIONBAR_DELAY_KEY + ":" + event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this) {
            if (timer != null) {
                timer.cancel();
            }
        }
    }

    /**
     * Load values from the config
     * @return Whether the config was successfully loaded
     */
    public abstract boolean loadConfig();
}