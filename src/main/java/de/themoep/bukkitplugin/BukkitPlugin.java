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
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
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
import org.bukkit.event.server.PluginDisableEvent;
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

public abstract class BukkitPlugin extends JavaPlugin implements Listener {

    private Timer timer;
    private boolean enableCalled = false;
    private static final String INFO_CHANNEL = "bukkitplugin:info";

    private boolean informUser = false;
    private String license = null;
    private String sourceLink = null;

    public BukkitPlugin() {
        super();
        init();
    }

    protected BukkitPlugin(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, final File file) {
        super(loader, description, dataFolder, file);
        init();
    }

    private void init() {
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
            sourceLink = descriptionConfig.getString("source");
        }
    }

    @Override
    public void onEnable() {
        enableCalled = true;
        saveDefaultConfig();
        reloadConfig();
        loadConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, INFO_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, INFO_CHANNEL, (channel, player, data) -> sendInfo(player));
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand(getName().toLowerCase(Locale.ROOT));
        if (command != null && command.getPlugin() == this) {
            command.setExecutor(this);
        } else {
            getLogger().severe("Unable to register plugin as it was not defined in the plugin.yml?");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (shouldInformUser() || sender.hasPermission(getName().toLowerCase(Locale.ROOT) + ".command.info")) {
            if ("info".equalsIgnoreCase(args[0]) || "license".equalsIgnoreCase(args[0])) {
                for (Map.Entry<String, String> entry : getPluginInfo().entrySet()) {
                    if (entry.getValue() != null) {
                        sender.sendMessage(ChatColor.GRAY + WordUtils.capitalizeFully(entry.getKey()) + ": " + ChatColor.WHITE + entry.getValue());
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

    public boolean shouldInformUser() {
        return informUser;
    }

    public String getLicense() {
        return license;
    }

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
                "license", getLicense(),
                "source", getSourceLink()
        );
    }

    private void sendInfo(Player player) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        for (Map.Entry<String, String> entry : getPluginInfo().entrySet()) {
            if (entry.getValue() != null) {
                out.writeUTF(entry.getKey());
                out.writeUTF(entry.getValue());
            }
        }

        player.sendPluginMessage(this, INFO_CHANNEL, out.toByteArray());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (shouldInformUser()) {
            sendInfo(event.getPlayer());
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