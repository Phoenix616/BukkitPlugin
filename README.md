# BukkitPlugin

This is a library to make Bukkit plugin development easier. It is a wrapper around the Bukkit API that provides a more
 intuitive and easy-to-use interface with some utility like automatic reload and info command registration as well as
 copyright and source information printing.

## plugin.yml Extensions

The library extends the plugin.yml file format with some additional fields that are used by the library to provide
 additional functionality.

| Field           | Description                                                         |
|-----------------|---------------------------------------------------------------------|
| `contributors`  | The contributors to the plugin.                                     |
| `license`       | The license name of the plugin.                                     |
| `license-terms` | The license terms of the plugin.                                    |
| `inform-user`   | Whether to inform users of the plugin about the license and source. |
| `source`        | The source code URL of the plugin.                                  |

## Usage

To use the library, you need to add it as a dependency to your project. You can do this by adding the following to your pom.xml file (and shading it in):

```xml
<repositories>
    <repository>
        <id>minebench-repo</id>
        <url>https://repo.minebench.de/</url>
    </repository>
    ...
</repositories>

<dependencies>
    <dependency>
        <groupId>de.themoep</groupId>
        <artifactId>bukkitplugin</artifactId>
        <version>1.0-SNAPSHOT</version><!-- Replace with the latest version! -->
        <scope>compile</scope>
    </dependency>
    ...
</dependencies>
```

Then you can create a new class that extends the `BukkitPlugin` class and implement the required methods:

```java
import de.themoep.bukkitplugin.BukkitPlugin;

public class MyPlugin extends BukkitPlugin {
    
    @Override
    public void onEnable() {
        super.onEnable();
        // Make sure to call the super method to initialize the BukkitPlugin
        // otherwise the plugin can't load the config, register commands
        // and will fail to start!
    }
    
    @Override
    public boolean loadConfig() {
        // The load method is automatically called on plugin enable as well as
        // when running `/myplugin reload` (if you added a command with your
        // plugin's name for your plugin in the plugin.yml)
        return true;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Your custom argument handlers...
        return super.onCommand(sender, command, label, args);
        // Make sure to call the super method to handle the info and reload subcommand provided by BukkitPlugin
    }
}
```

## License

This library is licensed under the LGPLv3 license. You can find the full license text in the [LICENSE](LICENSE) file.

```
BukkitPlugin
Copyright (c) 2024 Max Lee aka Phoenix616 (max@themoep.de)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```