package de.maxfehmer.TwitterWhitelist;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;



public class TwitterAPI {
	
	public static void registerAccount(final Player p){
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
		  .setOAuthConsumerKey(MainClass.twKey)
		  .setOAuthConsumerSecret(MainClass.twSecret);
		TwitterFactory tf = new TwitterFactory(cb.build());
		final Twitter twitter = tf.getInstance();
		
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
				try {
					RequestToken requestToken = twitter.getOAuthRequestToken();
					String authUrl=requestToken.getAuthorizationURL();
					TextComponent text = new TextComponent("--------------------------------------------\n"
														  + MainClass.getLangString("registerlink") +"\n"
														  +"--------------------------------------------");
					text.setColor(net.md_5.bungee.api.ChatColor.GOLD);
					text.setBold(true);
					text.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, authUrl));
					text.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(MainClass.getLangString("registerlinkhover")).create()));

					p.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GREEN + MainClass.getLangString("register1") +":\n");
					p.spigot().sendMessage(text);
					p.sendMessage(MainClass.getLangString("registerlink2") + ":\n"+(Object)ChatColor.GOLD + "/tww confirm " + (Object)ChatColor.ITALIC + "<Code>");
					MainClass.twRequestTokens.put(p.getUniqueId(),requestToken);
					//MainClass.plugin.getLogger().info("requestToken: "+requestToken);
				} catch (TwitterException e) {
					if (!twitter.getAuthorization().isEnabled()){
						MainClass.plugin.getLogger().warning("TwitterWhitelist Consumer Key/Secret is wrong or not set!");
					}
					if (401==e.getStatusCode()){
						p.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.RED + MainClass.getLangString("registererrorcode") +" /tww r");
					}else{
						p.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.RED + MainClass.getLangString("unknownerror"));
					}
					MainClass.plugin.getLogger().warning("Error on Registration: "+e.getMessage());
					//e.printStackTrace();
				}
			}
			}, 1L);
	}
	public static void confirmAccount(final String code, final Player p){
		boolean confirmState=false;
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
		  .setOAuthConsumerKey(MainClass.twKey)
		  .setOAuthConsumerSecret(MainClass.twSecret);
		TwitterFactory tf = new TwitterFactory(cb.build());
		final Twitter twitter = tf.getInstance();
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
				AccessToken accesstoken=null;
				if (!(MainClass.twRequestTokens.containsKey(p.getUniqueId()))){
					p.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.RED + MainClass.getLangString("confirmnotregistered"));
				}else{
					try {
						accesstoken = twitter.getOAuthAccessToken(MainClass.twRequestTokens.get(p.getUniqueId()),code);
					} catch (TwitterException e) {
						if (401==e.getStatusCode()){
							p.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.RED + MainClass.getLangString("registererrorcode"));
						}else{
							p.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.RED + MainClass.getLangString("unknownerror"));
						}
						//e.printStackTrace();
						MainClass.plugin.getLogger().warning("Couldn't get AccessToken for Player "+p.getName()+": "+e.getMessage());
					}
					if (!(accesstoken == null)){
						String twOAuthToken = accesstoken.getToken();
						String twOAuthSecret = accesstoken.getTokenSecret();
						String twOAuthUsername = accesstoken.getScreenName();
						Long twOAuthID = accesstoken.getUserId();
						MainClass.plugin.getLogger().info("Player "+p.getName()+" is known as @"+twOAuthUsername+" on Twitter.");

						//TODO: Lookup if ID already exists in DB and check if user is banned already. If so, force server to run command /ban PlayerName "Reason: Multiaccounting"
						PreparedStatement preparedStatementCheck = null;
						ResultSet resultSetCheck = null;
						 try {
							 preparedStatementCheck = MainClass.connection
							          .prepareStatement("SELECT twID, UUID, banned FROM twitterWhitelist WHERE twID=?");
						     preparedStatementCheck.setLong(1, twOAuthID);
						     resultSetCheck = preparedStatementCheck.executeQuery();
						      
						    if (resultSetCheck.isBeforeFirst() ) {
						    	resultSetCheck.next();
					    		//MainClass.plugin.getLogger().info("Player already "+p.getName()+" is in DB.");
								//check if banned
						    	if (resultSetCheck.getBoolean("banned")){
						    		//huensohn >.> BANN!!!!
						    		String commandLine = "ban "+p.getName()+" Multiaccount already banned.";
						    		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine);
						    	}else{
						    		//Feiner Junge. TROTZDEM MULTIACCOUNT!!!
						    		String commandLine = "ban "+p.getName()+" Multiaccount ban";
						    		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine);
						    	}
							}else{
								//nicht in DB. do nothing
							}
						} catch (SQLException e) {
							MainClass.plugin.getLogger().warning("Couldn't lookup Twitter data in DB: "+e.getMessage());
						} finally{
							try {
								if (preparedStatementCheck != null)
								preparedStatementCheck.close();
							} catch (SQLException e) {}
						}
						
						
						PreparedStatement preparedStatement = null;
						Statement statement = null;
						 try {
							 statement = MainClass.connection.createStatement();
							 preparedStatement = MainClass.connection
							          .prepareStatement("insert into twitterWhitelist values (?, ?, ?, ?, ?, ?, NULL)");
						      preparedStatement.setString(1, p.getUniqueId().toString());
						      preparedStatement.setString(2, p.getName());
						      preparedStatement.setString(3, twOAuthToken);
						      preparedStatement.setString(4, twOAuthSecret);
						      preparedStatement.setString(5, twOAuthUsername);
						      preparedStatement.setLong(6, twOAuthID);
						      preparedStatement.executeUpdate();
						} catch (SQLException e) {
							MainClass.plugin.getLogger().warning("Couldn't save Twitter data to DB: "+e.getMessage());
						} finally{
							try {
								if (statement != null)
								statement.close();
							} catch (SQLException e) {

							}
							if(MainClass.tweetEnabled){
								ConfigurationBuilder cb = new ConfigurationBuilder();
								cb.setDebugEnabled(true)
								  .setOAuthConsumerKey(MainClass.twKey)
								  .setOAuthConsumerSecret(MainClass.twSecret)
								  .setOAuthAccessToken(twOAuthToken)
								  .setOAuthAccessTokenSecret(twOAuthSecret);
								TwitterFactory tf = new TwitterFactory(cb.build());
								Twitter twitter = tf.getInstance();
								try {
									Status status = twitter.updateStatus(MainClass.getLangString("registertweet"));
									//status.getText();
								} catch (TwitterException e) {
									//e.printStackTrace();
								}
								
							}
							p.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GREEN + MainClass.getLangString("registersuccess"));
							MainClass.unwhitelisteds.remove(p.getPlayer().getUniqueId());
							p.setGameMode(GameMode.SURVIVAL);
						}

					}
				}
			}
			}, 1L);
	}
	
	public static void updateAccount(String aToken, String aTokenSecret, final Player p){
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
		  .setOAuthConsumerKey(MainClass.twKey)
		  .setOAuthConsumerSecret(MainClass.twSecret)
		  .setOAuthAccessToken(aToken)
		  .setOAuthAccessTokenSecret(aTokenSecret);
		TwitterFactory tf = new TwitterFactory(cb.build());
		final Twitter twitter = tf.getInstance();
		
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
				try {
					String newUserName = twitter.getScreenName();
					PreparedStatement preparedStatement = null;
					Statement statement = null;
					 try {
						 statement = MainClass.connection.createStatement();
						 preparedStatement = MainClass.connection
						          .prepareStatement("UPDATE twitterWhitelist SET twName=?, MCName=? WHERE UUID=?");
					      preparedStatement.setString(3, p.getUniqueId().toString());
					      preparedStatement.setString(2, p.getName());
					      preparedStatement.setString(1, newUserName);
					      preparedStatement.executeUpdate();
					      MainClass.plugin.getLogger().info("Updated Player "+p.getName()+"'s Twitter data");
					} catch (SQLException e) {
						MainClass.plugin.getLogger().warning("Couldn't update Twitter data to DB: "+e.getMessage());
					} finally{
						try {
							if (statement != null)
							statement.close();
						} catch (SQLException e) {}
					}
				} catch (TwitterException e) {
					MainClass.plugin.getLogger().warning("Error on updating Twitter account: "+e.getMessage());
					//e.printStackTrace();
				}
			}
			}, 1L);
	}
	
	public static void lookupUser(final Player p, final Player sender){
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
					PreparedStatement preparedStatement = null;
					ResultSet resultSet = null;
					 try {
						 preparedStatement = MainClass.connection
						          .prepareStatement("SELECT twName, UUID FROM twitterWhitelist WHERE UUID=?");
					      preparedStatement.setString(1, p.getUniqueId().toString());
					      resultSet = preparedStatement.executeQuery();
					      
					      if (resultSet.isBeforeFirst() ) {
				    			//MainClass.plugin.getLogger().info("Player "+p.getName()+" is in DB.");
								resultSet.next();
								if (resultSet.getString("UUID") != null){
									//message tw link from this user
									TextComponent text = new TextComponent(">@"+resultSet.getString("twName")+"<");
									text.setColor(net.md_5.bungee.api.ChatColor.GOLD);
									text.setBold(true);
									text.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "http://twitter.com/"+resultSet.getString("twName")));
									text.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(MainClass.getLangString("usertwitterhover")).create()));
									
									
									//FIXME: How to combine two TextComponents
									//TODO: Translate after fix
									TextComponent twbeforelink = new TextComponent((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p.getName() + (Object)ChatColor.WHITE + "ist auf Twitter als ");
									sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p.getName() + (Object)ChatColor.WHITE +" ist auf Twitter als");
									sender.spigot().sendMessage(twbeforelink + text);
									sender.sendMessage((Object)ChatColor.WHITE + "zu finden!");
								}
							}else{
								//nicht gewhitelistet aka nicht in DB.
								sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p.getName() + (Object)ChatColor.WHITE +" "+ MainClass.getLangString("messagenotwhitelisted"));
							}
					} catch (SQLException e) {
						MainClass.plugin.getLogger().warning("Couldn't lookup Twitter data in DB: "+e.getMessage());
					} finally{
						try {
							if (preparedStatement != null)
							preparedStatement.close();
						} catch (SQLException e) {}
					}
			}
			}, 1L);
	}
	
	public static void lookupOfflineUser(final String p, final Player sender){
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
					PreparedStatement preparedStatement = null;
					ResultSet resultSet = null;
					try {
						 preparedStatement = MainClass.connection
						          .prepareStatement("SELECT twName, UUID FROM twitterWhitelist WHERE MCName=?");
					      preparedStatement.setString(1, p);
					      resultSet = preparedStatement.executeQuery();
					      
					      if (resultSet.isBeforeFirst() ) {
				    			MainClass.plugin.getLogger().info("Offline Player "+p+" is in DB.");
								resultSet.next();
								if (resultSet.getString("UUID") != null){
									//message tw link from this user
									TextComponent text = new TextComponent(">@"+resultSet.getString("twName")+"<");
									text.setColor(net.md_5.bungee.api.ChatColor.GOLD);
									text.setBold(true);
									text.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "http://twitter.com/"+resultSet.getString("twName")));
									text.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Klicke hier um Twitter zu besuchen!").create()));

									//FIXME: Same as function above.
									sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p + (Object)ChatColor.WHITE +" ist auf Twitter als");
									sender.spigot().sendMessage(text);
									sender.sendMessage((Object)ChatColor.WHITE + "zu finden!");
								}
							}else{
								//nicht gefunden.
								sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p + (Object)ChatColor.WHITE +" " + MainClass.getLangString("messagenotwhitelisted"));
							}
					} catch (SQLException e) {
						MainClass.plugin.getLogger().warning("Couldn't lookup Twitter data in DB: "+e.getMessage());
					} finally{
						try {
							if (preparedStatement != null)
							preparedStatement.close();
						} catch (SQLException e) {}
					}
			}
			}, 1L);
	}
	
	public static void removeUser(final Player p, final Player sender){
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
					PreparedStatement preparedStatement = null;
					int results;
					 try {
						preparedStatement = MainClass.connection
						          .prepareStatement("DELETE FROM twitterWhitelist WHERE UUID=? LIMIT 1");
					    preparedStatement.setString(1, p.getUniqueId().toString());
					    results = preparedStatement.executeUpdate();
					    if (results == 0){
					    	sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p.getName() + (Object)ChatColor.RED +" " + MainClass.getLangString("adminrmusererr"));
					    }else{
					    	sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p.getName() + (Object)ChatColor.GREEN +" " + MainClass.getLangString("adminrmusersucc"));
					    }
						//TODO: Show error message to the sender
					} catch (SQLException e) {
						MainClass.plugin.getLogger().warning("Couldn't lookup Twitter data in DB: "+e.getMessage());
					} finally{
						try {
							if (preparedStatement != null)
							preparedStatement.close();
						} catch (SQLException e) {}
					}
			}
			}, 1L);
	}

	public static void removeOfflineUser(final String p, final Player sender){
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
					PreparedStatement preparedStatement = null;
					int results;
					 try {
						 preparedStatement = MainClass.connection
						          .prepareStatement("DELETE FROM twitterWhitelist WHERE MCName=? LIMIT 1");
					      preparedStatement.setString(1, p);
					      results = preparedStatement.executeUpdate();
					      if (results == 0){
					    	  sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p + (Object)ChatColor.RED +" " + MainClass.getLangString("adminrmofflineusererr"));
					      }else{
					    	  sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p + (Object)ChatColor.GREEN +" " + MainClass.getLangString("adminrmofflineusersucc"));
					      }
					} catch (SQLException e) {
						MainClass.plugin.getLogger().warning("Couldn't lookup Twitter data in DB: "+e.getMessage());
					} finally{
						try {
							if (preparedStatement != null)
							preparedStatement.close();
						} catch (SQLException e) {}
					}
			}
			}, 1L);
	}

	
	public static void getDBstats(final Player sender){
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
					PreparedStatement preparedStatement = null;
					Statement s = null;
					ResultSet results = null;
					int anzahlUser=0;
					String lastUserTw=null;
					String lastUserMC=null;
					String lastUserTime=null;
					
					try {
						s = MainClass.connection.createStatement();
						results = s.executeQuery("SELECT COUNT(*) AS countall FROM twitterWhitelist");
						if (results.next()) {
							anzahlUser=results.getInt("countall");
						}
					} catch (SQLException e) {
						MainClass.plugin.getLogger().warning("Couldn't fetch stats data from DB: "+e.getMessage());
					} finally{
						try {
							if (results != null)
								results.close();
								
							if (preparedStatement != null)
								preparedStatement.close();
						} catch (SQLException e) {}
					}
					preparedStatement=null;
					results=null;
					try {
						 preparedStatement = MainClass.connection
						          .prepareStatement("SELECT MCName,twName,lastChange FROM twitterWhitelist ORDER BY lastChange DESC LIMIT 1");
					      results = preparedStatement.executeQuery();
					      if (results.isBeforeFirst() ) {
					    	  results.next();
					    	  lastUserTw=results.getString("twName");
					    	  lastUserMC=results.getString("MCName");
					    	  lastUserTime=results.getString("lastChange");
					      }
					} catch (SQLException e) {
						MainClass.plugin.getLogger().warning("Couldn't lookup Twitter data in DB: "+e.getMessage());
					} finally{
						try {
							if (preparedStatement != null)
							preparedStatement.close();
						} catch (SQLException e) {}
					}
					
					if (lastUserTw != null && lastUserMC != null && lastUserTime != null){
						//everthing is fine, gimme da statistics
						String outputNice=(Object)ChatColor.GOLD+MainClass.getLangString("statsusercount")+": "+(Object)ChatColor.WHITE+String.valueOf(anzahlUser)+"\n"
										 +(Object)ChatColor.GOLD+MainClass.getLangString("statslastupdated")+": "+(Object)ChatColor.WHITE+lastUserMC+" (@"+lastUserTw+")\n"
										 +(Object)ChatColor.GOLD+" | "+MainClass.getLangString("statstime")+": "+(Object)ChatColor.WHITE+lastUserTime;
						sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.RED + " "+MainClass.getLangString("stats")+":\n"+outputNice);
					}else{
						//something bad happends :c
						sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.RED + "Konnte keine Statistiken holen!");
					}
					
			}
			}, 1L);		
	}
	
	public static void adminUpdateAccounts(final Player sender, final Player p){
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
				PreparedStatement preparedStatement = null;
				ResultSet resultSet = null;
				String aToken=null;
				String aTokenSecret=null;
				
				try {
					 preparedStatement = MainClass.connection
					          .prepareStatement("SELECT twKey, twSecret, UUID FROM twitterWhitelist WHERE UUID=?");
				      preparedStatement.setString(1, p.getUniqueId().toString());
				      resultSet = preparedStatement.executeQuery();
				      
				      if (resultSet.isBeforeFirst() ) {
							resultSet.next();
							if (resultSet.getString("UUID") != null){
								aToken=resultSet.getString("twKey");
								aTokenSecret=resultSet.getString("twSecret");
							}
						}else{
							//nicht gefunden.
							sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p.getName() + (Object)ChatColor.RED +" wurde nicht gefunden bzw. hat sein/ihr Account noch nicht mit Twitter veknüpft.");
						}
				} catch (SQLException e) {
					MainClass.plugin.getLogger().warning("Couldn't lookup Twitter data in DB: "+e.getMessage());
				} finally{
					try {
						if (preparedStatement != null)
						preparedStatement.close();
					} catch (SQLException e) {}
				}
				if (aToken != null){
					ConfigurationBuilder cb = new ConfigurationBuilder();
					cb.setDebugEnabled(true)
					  .setOAuthConsumerKey(MainClass.twKey)
					  .setOAuthConsumerSecret(MainClass.twSecret)
					  .setOAuthAccessToken(aToken)
					  .setOAuthAccessTokenSecret(aTokenSecret);
					TwitterFactory tf = new TwitterFactory(cb.build());
					final Twitter twitter = tf.getInstance();
					
					try {
						String newUserName = twitter.getScreenName();
						preparedStatement = null;
						 try {
							 preparedStatement = MainClass.connection
							          .prepareStatement("UPDATE twitterWhitelist SET twName=?, MCName=? WHERE UUID=?");
						      preparedStatement.setString(3, p.getUniqueId().toString());
						      preparedStatement.setString(2, p.getName());
						      preparedStatement.setString(1, newUserName);
						      preparedStatement.executeUpdate();
						      MainClass.plugin.getLogger().info("Updated Player "+p.getName()+"'s Twitter data");
						      sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p.getName() + (Object)ChatColor.GREEN +" wurde geupdated.");
						} catch (SQLException e) {
							MainClass.plugin.getLogger().warning("Couldn't update Twitter data to DB: "+e.getMessage());
						} finally{
							try {
								if (preparedStatement != null)
								preparedStatement.close();
							} catch (SQLException e) {}
						}
					} catch (TwitterException e) {
						MainClass.plugin.getLogger().warning("Error on updating Twitter account: "+e.getMessage());
						//e.printStackTrace();
					}
				}
			}
			}, 1L);
	}

	public static void keepMySQLAlive(){
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
					PreparedStatement preparedStatement = null;
					 try {
						 preparedStatement = MainClass.connection
						          .prepareStatement("DO 1");
					     preparedStatement.executeQuery();
					} catch (SQLException e) {
						MainClass.plugin.getLogger().warning("Couldn't keep MySQL alive: "+e.getMessage());
						//MainClass.plugin.getPluginLoader().disablePlugin(MainClass.plugin);
					} finally{
						try {
							if (preparedStatement != null)
							preparedStatement.close();
						} catch (SQLException e) {}
					}
			}
			}, 1L);
	}
	
	
	
	public static void banUser(final Player sender, final Player p){
		MainClass.plugin.getServer().getScheduler().scheduleSyncDelayedTask(MainClass.plugin, new Runnable() {

			public void run() {
				PreparedStatement preparedStatement = null;
				Statement statement = null;
				 try {
					 statement = MainClass.connection.createStatement();
					 preparedStatement = MainClass.connection
					          .prepareStatement("UPDATE twitterWhitelist SET banned=? WHERE UUID=?");
				      preparedStatement.setString(2, p.getUniqueId().toString());
				      preparedStatement.setBoolean(1, true);
				      preparedStatement.executeUpdate();
				      MainClass.plugin.getLogger().info("Banned Player "+p.getName());
				      sender.sendMessage((Object)ChatColor.DARK_RED + MainClass.chatPrefix + (Object)ChatColor.GOLD + p.getName() + (Object)ChatColor.GREEN +" wurde gebannt.");
				} catch (SQLException e) {
					MainClass.plugin.getLogger().warning("Couldn't ban Player in DB: "+e.getMessage());
				} finally{
					try {
						if (statement != null)
						statement.close();
					} catch (SQLException e) {}
				}
			}
			}, 1L);
	}

}
