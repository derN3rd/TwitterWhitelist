package de.maxfehmer.TwitterWhitelist;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import twitter4j.*;
import twitter4j.auth.*;

//TODO: Make repeating task which does "DO 1" on MySQL every 4 minutes to keep alive

public class MainClass extends JavaPlugin implements Listener{
    public static MainClass plugin;
    public static String chatPrefix = "TwitterWhitelist: ";
    public static boolean tweetEnabled;
    private static String mySQLHost;
    private static String mySQLDB;
    private static Integer mySQLPort;
    private static String mySQLUser;
    private static String mySQLPass;
    public static String mySQLURL;
    public static String twKey;
    public static String twSecret;
    public static Map<UUID,RequestToken> twRequestTokens = new HashMap<UUID, RequestToken>();
    public static Connection connection;
    public static Set<UUID> unwhitelisteds = new HashSet<UUID>();

    public static MainClass getInstance() {
        return plugin;
    }

    public void onEnable() {
        plugin = this;
        this.getConfig().options().copyDefaults(true);
        this.saveConfig();
        tweetEnabled = this.getConfig().getBoolean("TwitterWhitelist.SendTweetAfterRegistration");
        mySQLHost = this.getConfig().getString("TwitterWhitelist.MySQL.Host");
        mySQLDB = this.getConfig().getString("TwitterWhitelist.MySQL.DB");
        mySQLPort = this.getConfig().getInt("TwitterWhitelist.MySQL.Port");
        mySQLUser = this.getConfig().getString("TwitterWhitelist.MySQL.User");
        mySQLPass = this.getConfig().getString("TwitterWhitelist.MySQL.Pass");
        twKey = this.getConfig().getString("TwitterWhitelist.Twitter.ConsumerKey");
        twSecret = this.getConfig().getString("TwitterWhitelist.Twitter.ConsumerSecret");
        mySQLURL = "jdbc:mysql://"+mySQLHost+":"+mySQLPort+"/"+mySQLDB;
        try { //We use a try catch to avoid errors, hopefully we don't get any.
            Class.forName("com.mysql.jdbc.Driver"); //this accesses Driver in jdbc.
        } catch (ClassNotFoundException e) {
            //e.printStackTrace();
        	this.getLogger().warning("Couldn't access jdbc driver: "+e.getMessage()+"\n");
        }
        try {
            connection = DriverManager.getConnection(mySQLURL,mySQLUser,mySQLPass);
        } catch (SQLException e) {
            //e.printStackTrace();
        	this.getLogger().warning("Couldn't open MySQL Connection: "+e.getMessage()+"\n");
        }
        
        setUpMySQL();
        this.getServer().getPluginManager().registerEvents(this, this);
        
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
        	public void run() {
        		//do stuff
        		TwitterAPI.keepMySQLAlive();
        	}
        }, 1L, 6000L);

    }
    
    public void onDisable(){
    	try {
            if(connection!=null && !connection.isClosed()){ 
            	connection.close(); 
            }
    	}catch(Exception e){
    		//e.printStackTrace();
    		this.getLogger().warning("Couldn't close MySQL Connection: "+e.getMessage()+"\n");
        }
    }
    
    public static String getLangString(String sentence){
		return plugin.getConfig().getString("TwitterWhitelist.lang."+MainClass.plugin.getConfig().getString("TwitterWhitelist.Language") + "."+ sentence);
	}


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    	//args[0] = commmand; args{1] is the first arg.....
        if (sender instanceof Player) {
        	
        	
            Player p = (Player)sender;
            if (cmd.getName().equalsIgnoreCase("tww")) {
                if (args.length < 1) {
                	String tempMes;
                	if (!p.hasPermission("TwitterWhitelist.Admin")){
                		//Kein Admin
                		tempMes = "\n|------------------by derN3rd---------------------|\n"
                				+ "| /tww r\n"
                				+ "|  Verknüpft dein Twitteraccount\n"
                				+ "| /tww confirm <Code>\n"
                				+ "|  Bestätigt dein Acc mit einem Code\n"
                				+ "| /twitter <Spielername>\n"
                				+ "|  Gibt einen Link zum Twitter-Account aus\n"
                				+ "|-------------------------------------------------|";
                	}else{
                		//Admin
                		tempMes = "\n|-------------------------------------------------|\n"
             			  	    + "| /tww r\n"
             			  	    + "|  Verknüpft dein Twitteraccount\n"
                				+ "| /tww confirm <Code>\n"
                				+ "|  Bestätigt dein Acc mit einem Code\n"
             			  	    + "| /twitter <Spielername>\n"
             			  	    + "|  Gibt einen Link zum Twitter-Account aus\n"
             			  	    + "| /tww reload\n"
             			  	    + "|  Lädt die config.yml neu\n"
             			  	    + "| /tww update\n"
             			  	    + "|  Updatet alle Twitter-Accounts\n"
             			  	    + "| /tww update <Playername>\n"
             			  	    + "|  Updatet den speziefischen Player\n"
             			  	    + "| /tww adm\n"
             			  	    + "|  Gibt Infos über die DB aus\n"
             			  	    + "| /tww adm rm <Playername>\n"
             			  	    + "|  Entfernt das Twitter-Account des Spielers\n"
             			  	    + "|-------------------------------------------------|";
                	}
                    p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + tempMes);
                } else {
                	if (args[0].equalsIgnoreCase("r") || args[0].equalsIgnoreCase("register")){
                		//check if already confirmed before
                		TwitterAPI.registerAccount(p);
                	}else if (args[0].equalsIgnoreCase("confirm")){
                		//check if already confirmed before
                		if (args.length > 1){
                			TwitterAPI.confirmAccount(args[1], p);
                		}else{
                			p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "Bitte gebe den Code ein. /tww confirm <Code>");
                		}
                	}else if (args[0].equalsIgnoreCase("reload")){
                		if (!p.hasPermission("TwitterWhitelist.Admin")) {
                            p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "Das darfst du leider nicht :c");
                        } else {
                            this.reloadConfig();
                            p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.GREEN + "config.yml wurde neu geladen.");
                        }
                	}else if (args[0].equalsIgnoreCase("adm")){
                		if(args.length > 1 && args[1].equalsIgnoreCase("rm")){
                			if (!p.hasPermission("TwitterWhitelist.Admin")){
                				p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "Das darfst du leider nicht :c");
                			}else{
                				if (args.length > 2){
                					Player arguPlayer = plugin.getServer().getPlayer(args[2]);
                					if(arguPlayer ==null){
                						//Player not found
                						TwitterAPI.removeOfflineUser(args[2], p);
                					}else{
                						TwitterAPI.removeUser(arguPlayer, p);
                					}
                				}else{
                					//no arg for rm given
                					p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "/tww adm rm <Spielername>");
                				}
                			}
                		}else if(args.length > 1 && args[1].equalsIgnoreCase("ban")){
                			if (!p.hasPermission("TwitterWhitelist.Admin")){
                				p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "Das darfst du leider nicht :c");
                			}else{
                				if (args.length > 2){
                					Player arguPlayer = plugin.getServer().getPlayer(args[2]);
                					if(arguPlayer ==null){
                						//Player not found
                						//TODO: Implement banning of offline players
                					}else{
                						TwitterAPI.banUser(p, arguPlayer);
                					}
                				}else{
                					//no arg for rm given
                					p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "/tww adm rm <Spielername>");
                				}
                			}
                		}else{//Only /tww adm was typed
                			TwitterAPI.getDBstats(p);
                		}
                		
                	}else if (args[0].equalsIgnoreCase("update")){
                		if (!p.hasPermission("TwitterWhitelist.Admin")){
                			p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "Das darfst du leider nicht :c");
                		}else{
                			if (args.length > 1){
                				//update specific user
                				Player arguPlayer = plugin.getServer().getPlayer(args[1]);
                				if (arguPlayer == null){
                					p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "Spieler nicht gefunden!");
                				}else{
                					TwitterAPI.adminUpdateAccounts(p, arguPlayer);
                				}
                			}else{
                				//update all
                				p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "Hier würden alle Player geupdatet werden, aber: Not implemented c:");
                			}
                		}
                		
                	}
                    
                }
            }else if (cmd.getName().equalsIgnoreCase("twitter")) {
                if (!p.hasPermission("TwitterWhitelist.Lookup")) {
                    p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "Das darfst du leider nicht :c");
                } else {
                	if (args.length < 1){
                		p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "Bitte gib einen Spielernamen an."+(Object)ChatColor.GOLD+" /twitter <Spielername>");
                	}else{
                		Player player = plugin.getServer().getPlayer(args[0]);
                		if (player==null){
                			p.sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.RED + "Spieler "+(Object)ChatColor.GOLD+args[0]+(Object)ChatColor.RED+" nicht gefunden bzw. nicht online. Beginne Offline-Suche...");
                			TwitterAPI.lookupOfflineUser(args[0], p);
                		}else{
                			TwitterAPI.lookupUser(player, p);
                		}
                	}
                }
            }
        }
        return false;
    }
    
    
    @EventHandler(priority=EventPriority.NORMAL)
    public void onPlayerPreCommand(PlayerCommandPreprocessEvent e){
    	if(unwhitelisteds.contains(e.getPlayer().getUniqueId())){
    		if (e.getMessage().equalsIgnoreCase("/tww") || e.getMessage().toLowerCase().startsWith("/tww ")){
        		e.setCancelled(false);
        	}else if(e.getMessage().equalsIgnoreCase("/support") || e.getMessage().toLowerCase().startsWith("/support ")){
        		e.setCancelled(false);
        	}else{
        		e.getPlayer().sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.WHITE + "Um auf diesem Server spielen zu können, musst du dich mithilfe eines Twitter Accounts freischalten. Dies kannst du wie folgt tun:\n"+(Object)ChatColor.GOLD+"/tww r");
        		e.setCancelled(true);
        	}
        }
    	/**if (!e.getPlayer().hasPermission("TwitterWhitelist.Admin") || !e.getPlayer().isOp()){
    		if(e.getMessage().equalsIgnoreCase("/plugins") || e.getMessage().equalsIgnoreCase("/pl") || e.getMessage().equalsIgnoreCase("/bukkit:plugins") || e.getMessage().equalsIgnoreCase("/bukkit:pl") || e.getMessage().equalsIgnoreCase("/bukkit:?") || e.getMessage().equalsIgnoreCase("/?") || e.getMessage().equalsIgnoreCase("/ver") || e.getMessage().equalsIgnoreCase("/version") || e.getMessage().equalsIgnoreCase("/icanhasbukkit") || e.getMessage().equalsIgnoreCase("/about")){
    			e.getPlayer().sendMessage((Object)ChatColor.RED + "Hm...");
    			e.setCancelled(true);
    		}
    	}**/
    }
    
    @EventHandler(priority=EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent e){
    	PreparedStatement preparedStatement = null;
    	ResultSet resultSet = null;
    	Boolean iswhitelisted = null;
    	
    	try {
    		preparedStatement = connection
    		          .prepareStatement("SELECT UUID, twKey, twSecret, twName, twID from twitterWhitelist WHERE UUID=? LIMIT 1");
    		preparedStatement.setString(1, e.getPlayer().getUniqueId().toString());
    		resultSet = preparedStatement.executeQuery();
    		
    		if (resultSet.isBeforeFirst() ) {
    			iswhitelisted=true;
    			plugin.getLogger().info("Player "+e.getPlayer().getName()+" is already in DB.");
				resultSet.next();
				if (resultSet.getString("UUID") != null){
					TwitterAPI.updateAccount(resultSet.getString("twKey"), resultSet.getString("twSecret"),e.getPlayer());
					if (resultSet.getBoolean("banned")){
						//ban the player
						//force the server to run ban command
						String commandLine = "ban "+e.getPlayer().getName()+" Multiaccount-Ban.";
			    		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine);
					}
				}
			}else{
				//nicht gewhitelistet, also sperren und nur tww befehle erlauben.
				iswhitelisted=false;
			}
		} catch (SQLException err) {
			plugin.getLogger().warning("Couldn't fetch user from db: "+err.getMessage());
		}finally{
			try {
				if (preparedStatement != null)
					preparedStatement.close();
			} catch (SQLException e1) {}
		}
    	
    	if (iswhitelisted != null && !iswhitelisted){
    		plugin.getLogger().info("Player" + e.getPlayer().getName() + " is NOT whitelisted, sending message to player.");
    		e.getPlayer().sendMessage((Object)ChatColor.DARK_RED + chatPrefix + (Object)ChatColor.WHITE + "Um auf diesem Server spielen zu können, musst du dich mithilfe eines Twitter Accounts freischalten. Dies kannst du wie folgt tun:\n"+(Object)ChatColor.GOLD+"/tww r");
    		e.getPlayer().setGameMode(GameMode.SPECTATOR);
    		unwhitelisteds.add(e.getPlayer().getUniqueId());
    	}else{
    		plugin.getLogger().info("Player" + e.getPlayer().getName() + " is whitelisted");
    	}
    }
    
    @EventHandler
    public void freeze(PlayerMoveEvent event)
    {
      if(unwhitelisteds.contains(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("TwitterWhitelist.IgnoreWhitelist")){
    	  event.setTo(event.getFrom());
      }
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event){
        if(unwhitelisteds.contains(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("TwitterWhitelist.IgnoreWhitelist")){
        	event.setCancelled(true);
        }
    }
    
    private void setUpMySQL(){
    	Statement statement1 = null;
    	int results;
    	try {
			statement1 = connection.createStatement();
			//FIXME: Lookup MySQl WIki if BOOLEAN is the right value for bools
			results = statement1.executeUpdate("CREATE TABLE IF NOT EXISTS `twitterWhitelist` ( `UUID` VARCHAR(255) NOT NULL, `MCName` VARCHAR(255) NOT NULL , `twKey` VARCHAR(255) NOT NULL , `twSecret` VARCHAR(255) NOT NULL , `twName` VARCHAR(64) NOT NULL , `twID` BIGINT NOT NULL , `lastChange` TIMESTAMP on update CURRENT_TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, `banned` BOOLEAN NOT NULL DEFAULT '0') ENGINE = MyISAM;");
		} catch (SQLException e) {
			//e.printStackTrace();
			plugin.getLogger().warning("Couldn't set up MySQL table for use: "+e.getMessage());
		} finally{
			try {
			      if (statement1 != null) {
			        statement1.close();
			      }
			}catch(Exception e){}
		}
    	
    }
    
}

