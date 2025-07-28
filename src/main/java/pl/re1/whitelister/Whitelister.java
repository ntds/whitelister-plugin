package pl.re1.whitelister;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;

public final class Whitelister extends JavaPlugin implements Listener, CommandExecutor {

    public static Whitelister instance;
    public static Database database;

    private void registerCommands() {
        try {
            PluginCommand whitelister = this.getCommand("whitelister");
            whitelister.setExecutor(this);
        } catch (NullPointerException err) {
            getLogger().info("Command execution failed, err: " + err.getMessage());
        }
    }

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Whitelister enabled!");

        Config.loadConfigs();

        database = new Database(Config.getConfig().getString("mysql.jdbc-url"));
        database.createUserTable();

        registerCommands();

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down Whitelister.");

        database.disconnect();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        boolean new_whitelisted = Config.getConfig().getBoolean("whitelist.allow-all");

        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        if (!database.userExists(uuid)) {
            database.addUser(uuid, name, new_whitelisted);
        }

        boolean isWhitelisted = database.isWhitelisted(uuid);

        if (!isWhitelisted) {
            String kick_message = Config.getMessageConfig().getString("messages.kick");
            // Using Deprecated API for compatibility with proxies
            player.kickPlayer(ChatColor.translateAlternateColorCodes('&', kick_message));
        }
    }

    private void setWhitelisted(String name_player, boolean whitelisted) {
        Player target = Bukkit.getPlayerExact(name_player);

        String name = target.getName();
        UUID uuid = target.getUniqueId();

        if (database.userExists(uuid)) {
            database.setWhitelisted(uuid, whitelisted);
        } else {
            database.addUser(uuid, name, whitelisted);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + Config.getMessageConfig().getString("messages.usage"));
            return true;
        }

        switch (args[0]) {
            case "allow":
                if (!sender.hasPermission("whitelist.manage")) {
                    sender.sendMessage(ChatColor.RED + Config.getMessageConfig().getString("messages.permission"));
                    return true;
                }

                setWhitelisted(args[1], true);
                break;
            case "deny":
                if (!sender.hasPermission("whitelist.manage")) {
                    sender.sendMessage(ChatColor.RED + Config.getMessageConfig().getString("messages.permission"));
                    return true;
                }

                setWhitelisted(args[1], false);
                break;
            case "list":
                Player[] players = database.getAllowedPlayers();

                if (players == null) {
                    sender.sendMessage(ChatColor.RED + Config.getMessageConfig().getString("messages.unknown-error"));
                    return true;
                }

                StringBuilder list = new StringBuilder();

                list.append("List of allowed players:");

                for (Player p : players) {
                    String name = p.getName();
                    list.append(String.format(" %s\n", name));
                }

                String list_string = list.toString().trim();

                sender.sendMessage(list_string);
                break;
            case "reload":
                if (!sender.hasPermission("whitelist.manage")) {
                    sender.sendMessage(ChatColor.RED + Config.getMessageConfig().getString("messages.permission"));
                    return true;
                }
                Config.loadConfigs();

                if (database != null) {
                    database.disconnect();
                }
                database = new Database(Config.getConfig().getString("mysql.jdbc-url"));
                database.createUserTable();

                sender.sendMessage(ChatColor.GREEN + Config.getMessageConfig().getString("messages.reloaded"));
                break;
            default:
                sender.sendMessage(ChatColor.YELLOW + Config.getMessageConfig().getString("messages.usage"));
                break;
        }

        return true;
    }
}
