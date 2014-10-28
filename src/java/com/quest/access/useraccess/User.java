
package com.quest.access.useraccess;

import com.quest.access.common.Logger;
import com.quest.access.common.UniqueRandom;
import com.quest.access.common.mysql.Database;
import com.quest.access.control.Server;
import com.quest.access.crypto.Security;
import com.quest.access.useraccess.verification.Action;
import com.quest.access.useraccess.verification.UserAction;
import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.json.JSONArray;
import org.json.JSONException;


/**
 *
 * @author constant oduol
 * @version 1.0(17/3/12)
 */

/**
 * <p>
 * A user is a person or object that has been assigned access rights to
 * server resources, a user is created by specifying the user name, password, host,
 * server the user belongs to and the privileges assignable to the user. When a user is created
 * he is assigned a unique ten digit user id and the user's privileges are saved in the PRIVILEGES table.
 * A user can also be created as having access to a given service.
 * Once a user is created he can log in and access the resources that are accessible to him
 * </p>
 * <p>
 * A user can be renamed after creation provided there is no existing user with
 * the same user name. Users can also be temporarily deleted i.e their details are moved
 * to the USER_HISTORY table
 * Users can also be disabled to prevent access to server resources.
 * </p>
 * <p>
 * When a user logs in to a server a new session is created to keep track of the user
 * logging out the user destroys the user's session
 * Every user has a temporary privilege associated with them, the system can assign
 * users resources that they don't have access to temporarily through their temporary privileges
 * </p>
 *
 */
public class User implements Serializable{

    /*
     * system generated id
     */
    private String systemUserId;
    
    /*
     * cache for existing users
     */
    private static ConcurrentHashMap<String, User> userCache=new ConcurrentHashMap();
    
    /*
     * The name of the host the user is connecting from eg localhost,::1, 127.0.0.1, '%'
     * '%' means a connection from any host
     * '%'.com means a connection from any host that ends with .com
     * 192.168.1.% means any host name starting with 192.168.1
     * 
     */
    private String hostName;
     /*
     * this is the unique user id
     */
    private int userId;
    /*
     * this counts the number of initialized users
     */
    private static int userCount;
    /*
     * the name of the user
     */
    private String userName;
    /*
     * the password of the user
     */
    private String pass;
    
    
    private String group;
    /*
     * this determines which user is superior to which user 
     * and overrides the system generated value from the users staticResourceGroups
     */
    
    private Double superiority;
     
    /*
     * this is where we are creating the user's account
     */
    private Server server;
    
    /*
     * this are the  resource groups the user has access to
     */
    private HashMap priv;
   
    /*
     * this is the time this user was created
     */
    private Date created;
    /**
     * constructs a user object, the user's details are stored in the server's database
     * in the USERS table
     * @param userName the desired userName of the new user
     * @param pass  the desired password of the new user, if this is not provided the default password of the server is used
     * @param host the host from which this user is expected to connect from
     * @param server the server in which this user is expected to operate
     * @param priv the permanent privileges that are accessible to this user.
     */
    public User(String userName,String pass,String host,Server server,Action action, PermanentPrivilege ... priv) throws UserExistsException{
      this(userName, pass, host, server, null,action, priv);
    }
    
    /**
     * constructs a user object, the user's details are stored in the server's database
     * in the USERS table 
     * @param userName the desired userName of the new user
     * @param pass the desired password of the new user, if this is not provided the default password of the server is used
     * @param host the host from which this user is expected to connect from
     * @param server  the server in which this user is expected to operate
     * @param group the user group that this user is being assigned to
     * @param priv the  permanent privileges that are accessible to this user.
     * @throws UserExistsException 
     */
    public User(String userName,String pass,String host, Server server,String group, Action action, PermanentPrivilege ... priv) throws UserExistsException{
      this.userName=userName;
      this.pass=pass;
      this.server=server;
      this.priv=new HashMap();
      this.group=group;
      userCount++;
      this.userId=userCount;
      this.hostName=host;
      this.superiority=assignSuperiority(priv);
      this.created=new Date();
      createUser(userName,pass,host,server,this.superiority,group,action);
        try {
            grantPrivileges(priv);
        } catch (NonExistentUserException ex) {
        }
    }
     /**
     * constructs a user object, the user's details are stored in the server's database
     * in the USERS table 
     * @param userName the desired userName of the new user
     * @param pass the desired password of the new user, if this is not provided the default password of the server is used
     * @param host the host from which this user is expected to connect from
     * @param server  the server in which this user is expected to operate
     * @param group the user group that this user is being assigned to
     * @param service the services this user is being assigned access to
     * @throws UserExistsException 
     */
    public User(String userName,String pass,String host, Server server,String group,Action action,Service... service) throws UserExistsException{
        PermanentPrivilege[] privs=new PermanentPrivilege[service.length];
        for(int x=0; x<service.length; x++){
            privs[x]=service[x].getServicePrivilege();
       }
        User user=new User(userName, pass, host, server,group,action,privs);
    }
   
     /**
     * constructs a user object, the user's details are stored in the server's database
     * in the USERS table 
     * @param userName the desired userName of the new user
     * @param pass the desired password of the new user, if this is not provided the default password of the server is used
     * @param host the host from which this user is expected to connect from
     * @param server  the server in which this user is expected to operate
     * @param service the services accessible to this user
     * @throws UserExistsException 
     */
    public User(String userName,String pass,String host, Server server,UserAction action,Service... service) throws UserExistsException{
        this(userName, pass, host, server, null,action, service);
     }
    
    
    /**
     * this creates a user object with specified parameters
     * @param userName
     * @param pass
     * @param host
     * @param serv
     * @param group
     * @throws UserExistsException 
     */
    public User(String userName,String pass,String host, Server serv,UserAction action, PresetGroup group) throws UserExistsException{
         ArrayList privs = group.getPermanentPrivileges();
             PermanentPrivilege [] prv=new PermanentPrivilege[privs.size()];
              for(int x=0; x<privs.size(); x++){
                prv[x]=(PermanentPrivilege)privs.get(x); 
               }
           User user=new User(userName, pass, host, serv, group.getGroupName(),action, prv);
          
    }
    /**
     * privately creates a user object which is used by the method getExistingUser()
     * @param userName the name of the user
     * @param serv  the server the user belongs to
     * @see #getExistingUser(java.lang.String, com.quest.access.net.Server) 
     */
    private User(String userName, Server serv, Date created){
      this.userName=userName;
      this.server=serv;
      userCount++;
      this.userId=userCount;
      this.created=created;
    }
    
    
    /**
     * this method returns the host name of the user as a string
     */
    public String getHostName(){
        return this.hostName;
    }
    
    /**
     * this method changes the host name of a user in the USERS table in the database
     */
    public void setHostName(String name){
        Database.setValue(this.server,"USERS","HOST",name,"USER_NAME='"+this.userName+"'");
        userCache.remove(this.userName);
        this.hostName=name;     
    }
   
    
    /**
     * 
     * @return the time when the user was created
     */
    public Date getCreationTime(){
       return this.created;    
    }

    /**
     * this method returns a unique Integer representing the user's id
     * these integers count up from 1,2,3 and so on as more user objects are created
     */
    public int getUserId(){
        return this.userId;
    }
    
    
    /**
     * this method returns the user name of a user
     */
    public String getUserName(){
        return this.userName;
    }
    
    /**
     * this method changes the username of a user
     * the user name in the database is also changed
     * @param newName the new name to assign to the user
     */
    public void setUserName(String newName){
        Database.setValue(this.server,"USERS","USER_NAME",newName,"USER_NAME='"+this.userName+"'"); 
        userCache.remove(this.userName);
        this.userName=newName;
    }
    
    
    
    /**
     * this method returns the number of users counted so far
     */
    public static int getUserCount(){
        return userCount;
    }
    
    /**
     * this method is used to modify the password of a user
     * @param newPass the new password the user will use to log in
     */
    public void setPassWord(String newPass){
        String dbName=this.server.getDatabase().getDatabaseName();
        try{
         byte[] bytes=Security.makePasswordDigest(this.userName,newPass.toCharArray());
          String passw=Security.toBase64(bytes);
           Database.executeQuery("UPDATE USERS SET PASS_WORD='"+passw+"' WHERE USER_NAME=?",dbName,this.userName);
          }
        catch(Exception e){
          Logger.toConsole(e, this.getClass());
       }
        this.pass=newPass;
    }
    
    /**
     * this method changes the group this user was initially assigned to the new group
     * @param group the new group of the user
     */
    public void setUserGroup(String group){
      Database.setValue(this.server,"USERS","GROUPS",group,"USER_NAME='"+this.userName+"'");
      userCache.remove(this.userName);
      this.group=group;
    }
    
    /**
     * 
     * @return the group this user was assigned to
     */
    public String getUserGroup(){
        return this.group;
    }
   
    /**
     * this method returns the server a user belongs to
     */
    public Server getServer(){
        return this.server;
    }
    
    public String getPassWord(){
        return this.pass;
    }
    
    /**
     * this method returns the system generated user id as a string for this user
     */
    
   public String getSystemUserId(){
       return this.systemUserId;
    }
   
   
    /**
     * this method returns a user's permanent privileges or null if none are defined
     */
    public HashMap getUserPrivileges(){
        return this.priv;
    }
    
    /**
     * this method checks whether a user exists in the users table, if the user has been deleted he is
     * then non existent till he is undeleted
     * @param userName the username of the user we want to ascertain his existence
     * @param serv the server the user was initially created in
     * @param tableName the table from which the users details are stored the default is 
     *                  the USERS table while deleted users are stored in USER_HISTORY
     */
    public static boolean ifUserExists(String userName,Server serv,String tableName){
       return Database.ifValueExists(userName,tableName,"USER_NAME",serv.getDatabase());
    }
    
    /**
     * This method gives us a double value indicating the rank of a user, generally 
     * the user superiority is scored by adding the values of the levels in the permanent privileges 
     * the user belongs to and then dividing by the number of permanent privileges
     * this system generated score can be overriden if a score is manually provided
     * through <code>setUserSuperiority(double sup)</code>
     * @return an integer representing the rank of a user in the system hierachy
     *         the higher the rank the more superior a user is considered
     */
    public Double getUserSuperiority(){
          return this.superiority;  
        
    }
    

    /**
     * this method manually changes the normally system generated value of this users superiority
     * @param sup the value that determines whether a user is superior to another
     */
    public void setUserSuperiority(Double sup){
        // we only deal with values above zero
        if(sup<0.0){
           return; 
         }
       
        Database.executeQuery("UPDATE USERS SET SUPERIORITY=? "
                + "WHERE USER_NAME=? ",this.server.getDatabase().getDatabaseName(),sup.toString(),this.userName);
        userCache.remove(this.userName);
        this.superiority=sup;
    }
   
    /**
     * This method deletes a user from the users table and moves his details to the USER_HISTORY table
     * if the user had a mysql account the account is dropped
     * @param userName the name of the user to be deleted
     * @param serv the server where this user was created
     */
    public static void deleteUser(String userName, Server serv) throws NonExistentUserException{
          String dbName=serv.getDatabase().getDatabaseName();
          //save details in user history
          Database.executeQuery("INSERT INTO USER_HISTORY SELECT * FROM USERS WHERE USER_NAME=?",dbName,userName);
             // delete the user
          permanentlyDeleteUser(userName, serv);
          userCache.remove(userName);
    }
    
    /**
     * this method permanently deletes a user and all his records
     * this method cannot be undone and user records cannot be recovered
     * @param userName the name of the user we want to permanently delete
     * @param serv the server the user was created in
     */
    public static void permanentlyDeleteUser(String userName, Server serv) throws NonExistentUserException{
        String dbName=serv.getDatabase().getDatabaseName();
        User user = User.getExistingUser(userName, serv);
        Database.executeQuery("DELETE FROM USERS WHERE USER_ID=?",dbName,user.getSystemUserId());
        Database.executeQuery("DELETE FROM PRIVILEGES WHERE USER_ID=?",dbName,user.getSystemUserId());
        userCache.remove(userName);
    }
    
    
    /**
     * this method returns the history of a users actions from the database
     * it gives a view of what a user has been doing, this actions only represent
     * those actions that the system chooses to record. specifying a limit of 0
     * returns all the records, caution should be used when specifying a limit of 0 since
     * it could be slow
     */
    
    public static ArrayList getActionHistory(String userName, Server serv, int limit){
        ResultSet set=null;
        if(limit==0){
           set = Database.executeQuery("SELECT * FROM USER_ACTIONS WHERE USER_NAME=? ORDER BY ACTION_TIME DESC", serv.getDatabase().getDatabaseName(), userName);
        }
        else if(limit>0){
           set = Database.executeQuery("SELECT * FROM USER_ACTIONS WHERE USER_NAME=? ORDER BY ACTION_TIME DESC LIMIT "+limit+"", serv.getDatabase().getDatabaseName(), userName); 
        }
        ArrayList history=new ArrayList();
         try {
            while(set.next()){
              HashMap details=new HashMap();
              details.put("USER_ID",set.getString("USER_ID"));
              details.put("ACTION_ID",set.getString("ACTION_ID"));
              details.put("ACTION_TIME",set.getString("ACTION_TIME"));
              details.put("ACTION_DESCRIPTION",set.getString("ACTION_DESCRIPTION"));
              history.add(details);
            }
            set.close();
            return history;
        } catch (SQLException ex) {
          return history;
        }
    }
    
    /**
     * this method permanently deletes the current user
     * @see #deleteUser(java.lang.String, com.quest.access.net.Server) 
     */
    public void permanentlyDeleteUser() throws NonExistentUserException{
       permanentlyDeleteUser(this.userName, this.server);
    }
   
    /**
     * this method restores a deleted user, the users details are moved from the USER_HISTORY
     * table to the users table and if this users account was a mysql account the account is recreated with
     * the same details
     * @param userName the username of the user we want to undelete
     * @param serv the server the user was initially created in
     * @see #deleteUser(java.lang.String, com.quest.access.useraccess.Server) 
     */
    public static void undeleteUser(String userName, Server serv) throws UserExistsException{
        if(Database.ifValueExists(userName,"USERS", "USER_NAME",serv.getDatabase())){
            throw new UserExistsException();
        }
        String dbName=serv.getDatabase().getDatabaseName();
        Database.executeQuery("INSERT INTO USERS SELECT * FROM USER_HISTORY WHERE USER_NAME=?",dbName,userName);
        Database.executeQuery("DELETE FROM USER_HISTORY WHERE USER_NAME=?",dbName,userName); 
    }
    
    /**
     * this method renames a user who has already been deleted and his details moved to the deleted users table
     * @param oldName the old user name this user used to login into his account
     * @param newName the new name we want to name this user
     * @param serv the server this user originally belonged to before being deleted
     * @throws UserExistsException 
     */
    public static void renameDeletedUser(String oldName,String newName, Server serv) throws UserExistsException{
      if(Database.ifValueExists(newName,"USER_HISTORY", "USER_NAME",serv.getDatabase())){
             throw new UserExistsException();
          }
       Database.setValue(serv,"USER_HISTORY","USER_NAME",newName,"USER_NAME='"+oldName+"'");
    }
    
    /**
     * This method makes a user disabled or not depending on the value of flag
     * once a user is disabled he will not be able to log in until he is enabled
     * again
     * @param flag this either true or false to say if this user is disabled or not
     * @param userName the user name of the user to be disabled
     * @param serv the server the specified user was created in
     */
    public static void disableUser(boolean flag,String userName, Server serv){
        try{
           if(flag==true){
                Database.setValue(serv, "USERS", "IS_DISABLED", "1", "USER_NAME='"+userName+"'");   
              }
              else{
                Database.setValue(serv, "USERS", "IS_DISABLED", "0", "USER_NAME='"+userName+"'");   
              }
             
        }
       catch(Exception e){
         
       }
    
    }
    
    /**
     * this method checks whether a user has been deleted and returns true
     * if the user has indeed been deleted otherwise it returns false
     * @param userName the name of the user we want check if he has been deleted
     * @param serv the server this user was created in or belongs to
     */
    public static boolean isUserDeleted(String userName, Server serv){
        String name=Database.getValue("USER_NAME","USER_HISTORY","USER_NAME",userName,serv);
          if(name!=null){
              return true;
          }
        return false;
    }
    
    /**
     * this method checks whether a user is logged in, if the user is logged in the method
     * returns true otherwise it returns false
     * @param userName the username of the user we want to check if he is logged in
     * @param serv the server in which this user was originally created
     */
    public static boolean isUserLoggedIn(String userName, Server serv){
      String value=Database.getValue("IS_LOGGED_IN","USERS","USER_NAME",userName,serv); 
        if("1".equals(value)){
            return true;
        }
        return false;
    }
    
    /**
     * this method checks whether a user is disabled, if the user is indeed disabled the
     * method returns true otherwise the method returns false
     * @param userName the username of the user we want to check if he is disabled
     * @param serv the server in which this user was originally created
     */
    public static boolean isUserDisabled(String userName, Server serv){
      String value=Database.getValue("IS_DISABLED","USERS","USER_NAME",userName,serv); 
        if("1".equals(value)){
            return true;
        }
        return false;
    }
   
    
    /**
     * this method marks the user as logged out in the database
     * @param userName the user name of the we need to mark as logged out
     * @param serv the server this user was originally created
     */
    public static void forceLogoutUser(String userName, Server serv){
      Database.setValue(serv,"USERS","IS_LOGGED_IN","0","USER_NAME='"+userName+"'");
    }

/**
 * this method returns the privileges of a user as stored in the database
 * this method is called when a user logs in in order to determine which privileges
 * the user has, privilege information is stored in the PRIVILEGES table.
 * the privilege information is returned in a hash map containing the RESOURCE GROUP NAMES
 * as the keys of the hash map and an array list containing the users resource names
 *@param userName the username of the user we want to obtain privileges
 *@param serv the server where the user was originally created 
 */

    
private static HashMap getUserPrivileges(User user, Server serv){
  HashMap<String, ArrayList<String>> privileges=new HashMap();
   String userID=user.getSystemUserId();
  
      String dbName=serv.getDatabase().getDatabaseName();
      String psql="SELECT RESOURCES.NAME as RESOURCE_NAME, RESOURCE_GROUPS.NAME as RESOURCE_GROUP_NAME "+
            " FROM RESOURCES,RESOURCE_GROUPS,PRIVILEGES "+
            " WHERE RESOURCES.GROUP_ID=PRIVILEGES.GROUP_ID AND "+
            " RESOURCE_GROUPS.GROUP_ID=PRIVILEGES.GROUP_ID AND RESOURCES.GROUP_ID = PRIVILEGES.GROUP_ID AND PRIVILEGES.USER_ID=?";
        ResultSet set = Database.executeQuery(psql,dbName,userID);
       try{ 
        while(set.next()){
                String resourceName = set.getString("RESOURCE_NAME");
                String resourceGroupName = set.getString("RESOURCE_GROUP_NAME");
                // check that we havent stored this resource group before
                 if(!privileges.containsKey(resourceGroupName)){
                     privileges.put(resourceGroupName, new ArrayList());
                     privileges.get(resourceGroupName).add(resourceName);
                 }
                 else{
                  // this resource group already exists   
                   privileges.get(resourceGroupName).add(resourceName);  
                 }
            }
          set.close();
          return privileges;
         }
       catch(Exception e){
        return privileges;
       }
  
}



/**
 * this method empties all cached user objects
 */
public static void emptyUserCache(){
    userCache.clear();
}



 public static boolean changePassword(Server serv, String userName, String oldPass, String newPass){
        String dbName=serv.getDatabase().getDatabaseName();
        try {
            String old_pass=Security.toBase64(Security.makePasswordDigest(userName, oldPass.toCharArray()));
            String pass_stored=Database.getValue("PASS_WORD","USERS","USER_NAME",userName,serv);
            if(old_pass.equals(pass_stored)){
                    byte[] bytes=Security.makePasswordDigest(userName,newPass.toCharArray());
                    String passw=Security.toBase64(bytes);
                    long time=System.currentTimeMillis();
                    Database.executeQuery("UPDATE USERS SET PASS_WORD='"+passw+"',IS_PASSWORD_EXPIRED='"+time+"' WHERE USER_NAME=?",dbName,userName);
                    return true;
              }
              else{
                return false;
              }
         
        } catch (Exception ex) {
           return false;
        }
     }
    
    /**
     * this method checks whether a users password has expired, if the password has expired
     * the method returns true
     * @param userName the username of the user we want to check if his password has expired
     * @param serv the server in which this user was originally created
     */
    
    public static long getPasswordExpiry(String userName, Server serv){
      String value=Database.getValue("IS_PASSWORD_EXPIRED","USERS","USER_NAME",userName,serv);
        return Long.parseLong(value);
    }
   
    /**
     * this method returns login details for the specified user , each time a user logs in
     * the login details at that time are inserted in one table row of the LOGIN table 
     * specifying a limit of 0 returns all the results, specifying a limit of zero should
     * be used with caution since it could be slow.
     * details return include the login time, login id etc. an arraylist containing hashmaps of the login 
     * data is returned 
     * the keys in the hash map are
     *  <ol>
     *    <li>SERVER_IP the ip address of the server machine the user logged in from</li>
     *    <li>CLIENT_IP the ip address of the client machine the user logged in from</li>
     *    <li>LOGIN_ID the system generated log in id </li>
     *     <li>LOGIN_TIME the time the user logged in</li>
     *  </ol>
     * @param userName the username of the user we want to retrieve the login details
     * @param serv the server this user was originally created in
     * @param limit the number of rows to be retrieved
     * @return an arraylist containing login details
     */
    
    public static ArrayList getLoginLog(String userName, Server serv, int limit){
        ArrayList loginLog=new ArrayList();
        String dbName=serv.getDatabase().getDatabaseName();
        ResultSet set=null;
         if(limit==0){
           set = Database.executeQuery("SELECT * FROM LOGIN WHERE USER_NAME=? ORDER BY LOGIN_TIME DESC", dbName, userName); 
           }
          else if(limit>0){
           set = Database.executeQuery("SELECT * FROM LOGIN WHERE USER_NAME=? ORDER BY LOGIN_TIME DESC LIMIT "+limit+"", dbName, userName);
          }
         try{
           while(set.next()){
                HashMap details=new HashMap();
                details.put("SERVER_IP",set.getString("SERVER_IP"));
                details.put("CLIENT_IP",set.getString("CLIENT_IP"));
                details.put("LOGIN_ID",set.getString("LOGIN_ID"));
                details.put("LOGIN_TIME",set.getString("LOGIN_TIME"));
            
                loginLog.add(details);
           }
          set.close();
          return loginLog;
        }
       catch(Exception e){
          return loginLog;
       }
    }
    
    /**
     * this method returns the details of a user who has successfully logged out of the system
     * such details include the logout time, logout id etc. an arraylist containing hashmaps of the logout 
     * data is returned 
     * the keys in the hash map are
     *  <ol>
     *    <li>SERVER_IP the ip address of the server machine the user logged out from</li>
     *    <li>CLIENT_IP the ip address of the client machine the user logged out from</li>
     *    <li>LOGOUT_ID the system generated log out id which is the same as the login id</li>
     *     <li>LOGOUT_TIME the time the user logged out</li>
     *  </ol>
     * @param userName the username of the user we want to retrieve the logout details
     * @param serv the server this user was originally created in
     * @param limit the number of rows to be retrieved
     * @return an arraylist containing logout details
     */
    public static ArrayList getLogoutLog(String userName, Server serv, int limit){
      ArrayList logoutLog=new ArrayList();
        String dbName=serv.getDatabase().getDatabaseName();
        ResultSet set=null;
         if(limit==0){
           set = Database.executeQuery("SELECT * FROM LOGOUT WHERE USER_NAME=? ORDER BY LOGOUT_TIME DESC", dbName, userName); 
           }
          else if(limit>0){
           set = Database.executeQuery("SELECT * FROM LOGOUT WHERE USER_NAME=? ORDER BY LOGOUT_TIME DESC LIMIT "+limit+"", dbName, userName);
          } 
          try{
           while(set.next()){
                HashMap details=new HashMap();
                details.put("SERVER_IP",set.getString("SERVER_IP"));
                details.put("CLIENT_IP",set.getString("CLIENT_IP"));
                details.put("LOGOUT_ID",set.getString("LOGOUT_ID"));
                details.put("LOGOUT_TIME",set.getString("LOGOUT_TIME"));
                logoutLog.add(details);
             }
           set.close();
          return logoutLog;
        }
         
       catch(Exception e){
          return logoutLog;
       }   
    }
    /**
     * this method returns an instance of an existing user without trying to recreate the
     * user, the method gets the details of the user and creates a user object for this user that
     * already exists
     *
     */
    public static User getExistingUser(String userName,Server serv) throws NonExistentUserException{
        boolean exists=userCache.containsKey(userName);
         if(exists){
             return (User)userCache.get(userName);
         }
        User user=null;
          String pass="";
          String host=""; 
          double sup=0.0;
          String group="";
          String userId="";
          Date created=null;
           // make sure the user exists 
        if(ifUserExists(userName, serv, "USERS")){
            String dbName=serv.getDatabase().getDatabaseName();
            ResultSet set = Database.executeQuery("SELECT * FROM USERS "
                    + "WHERE USER_NAME=? ",dbName,userName);
             try{
                while(set.next()){
                     pass = set.getString("PASS_WORD");
                     host = set.getString("HOST");
                     sup = set.getDouble("SUPERIORITY");
                     group=set.getString("GROUPS");
                     userId=set.getString("USER_ID");
                     created=set.getDate("CREATED");
                     
                } 
                set.close();
                user=new User(userName, serv,created);
                user.setPass(pass);
                user.setHost(host);
                user.setSuperiority(sup);
                user.setGroup(group);
                user.setUserId(userId);
                user.setUserPrivileges(getUserPrivileges(user, serv));
                userCache.put(userName, user);
                return user;
               }
             catch(Exception e){
               return user;
             }
          }
        else{
            throw new NonExistentUserException();
        }
    }
    
    /**
     * this method assigns the specified privileges to the specified user
     * @param userName the user name of the user to be assigned privileges
     * @param serv the server the user was created in
     * @param priv the privileges to be assigned
     */
    public  void grantPrivileges(PermanentPrivilege... priv) throws NonExistentUserException{
         String dbName=this.server.getDatabase().getDatabaseName();
         for(int x=0; x<priv.length; x++){
             String id=priv[x].getPermanentPrivilegeID();
             if(id==null){
               continue;
               // this means no such resource group exists
                }
              try{
                  ResultSet set = Database.executeQuery("SELECT * FROM PRIVILEGES WHERE"
                        + " USER_ID=? AND GROUP_ID=?", dbName, this.getSystemUserId(),id);
                   String userID=null;
                   String groupID=null;
                   while(set.next()){
                       userID=set.getString("USER_ID"); 
                       groupID=set.getString("GROUP_ID"); 
                      }
                   if(userID==null && groupID==null){
                        if("".equals(id)){
                              
                          }
                          else{
                            Database.executeQuery("INSERT INTO PRIVILEGES VALUES(?,?)",dbName,this.getSystemUserId(),id);
                            setSuperiority(true, priv);
                            removeFromUserCache(userName);
                            Logger.toConsole("user privileges saved", this.getClass());
                       }
                     
                 }
               // this combination exists so no need to add again if we reach this point
            }
            catch(Exception e){
              throw new NonExistentUserException();
            }
       }
    }
    
    /**
     * this method revokes the specified privileges from the specified users
     * @param userName the user name of the user to be revoked privileges
     * @param serv the server the user was created in
     * @param priv the privileges to be revoked
     */
    public  void revokePrivileges(PermanentPrivilege...priv) throws NonExistentUserException{
       try{
       String dbName=this.server.getDatabase().getDatabaseName();
        for(int x=0; x<priv.length; x++){
            String id=priv[x].getPermanentPrivilegeID();
            if(id==null){
                // this means no such privilege exists
              continue;
             }
            Database.executeQuery("DELETE FROM PRIVILEGES WHERE USER_ID=? AND GROUP_ID=? ",dbName,this.getSystemUserId(),id);
            setSuperiority(false, priv);
            removeFromUserCache(userName);
            Logger.toConsole("user privileges revoked", this.getClass());
       }
     }
       catch(Exception e){
        throw new NonExistentUserException();
       }
    }
    
    public ArrayList getAccessibleApps(){
        try {
            Database db=this.server.getDatabase();
            ResultSet set = db.execute("SELECT APPLICATION_NAME FROM APPLICATIONS,"
                                                 + "USER_APPS WHERE APPLICATIONS.APPLICATION_ID=USER_APPS.APPLICATION_ID "
                                                 + "AND USER_APPS.USER_ID='"+this.getSystemUserId()+"'");
            ArrayList apps=new ArrayList();
             while(set.next()){
                   apps.add(set.getString("APPLICATION_NAME"));
             }
             return apps;
        } catch (Exception ex) {
            Logger.toConsole(ex, this.getClass());
            return null;
        }
    }
    
    public void setAccessibleApps(JSONArray apps){
        Database db=this.server.getDatabase();
        for(int x=0; x<apps.length(); x++){
            try {
                ResultSet set = Database.executeQuery("SELECT USER_ID,APPLICATION_ID "
                        + "FROM USER_APPS WHERE USER_ID=? AND APPLICATION_ID=(SELECT APPLICATION_ID FROM APPLICATIONS WHERE APPLICATION_NAME=?)",db.getDatabaseName(),this.getSystemUserId(),(String)apps.get(x) );
                String appID;
                String userID;
                try {
                    while(set.next()){
                        appID = set.getString("APPLICATION_ID");
                        userID= set.getString("USER_ID");
                        if(appID!=null && userID!=null){
                            //they already exist
                           return;
                        }
                    }
                  String subQuery="!(SELECT APPLICATION_ID FROM APPLICATIONS WHERE APPLICATION_NAME='"+apps.get(x) +"')";
                  db.doInsert("USER_APPS", new String[]{subQuery,this.getSystemUserId(),"!NOW()"}); 
                } catch (SQLException ex) {
                    Logger.toConsole(ex,User.class);
                } 
             
             } catch (JSONException ex) {
                java.util.logging.Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
            } 
         
         }
    }
    
  
    
    public void removeAccessibleApp(String appName){
       Database.executeQuery("DELETE FROM USER_APPS WHERE"
               + " USER_ID=? AND APPLICATION_ID=(SELECT APPLICATION_ID FROM APPLICATIONS WHERE APPLICATION_NAME=?)",this.server.getDatabase().getDatabaseName(),this.getSystemUserId(), appName);
    }
    
    public static String getAppUrl(Server serv,String appName){
        ResultSet set = Database.executeQuery("SELECT APPLICATION_URL FROM APPLICATIONS "
                                         + "WHERE APPLICATION_NAME=?",serv.getDatabase().getDatabaseName(),appName);
        String url="";
        try {
            while(set.next()){
              url=set.getString("APPLICATION_URL"); 
            }
          return url;
        } catch (SQLException ex) {
            Logger.toConsole(ex, User.class);
            return url;
        }
    }
    
    public void removeAccessibleApps() throws NonExistentUserException{
        Database db=this.server.getDatabase();
        db.execute("DELETE FROM USER_APPS WHERE USER_ID='"+this.getSystemUserId()+"'");
    }
    
    /**
     * returns a string representation of a user
     */
    @Override
    public String toString(){
       return "User["+this.userName+" : "+this.userId+"]"; 
    }
    
    /**
     * this method compares two users, users are only equal if they belong to
     * the same server and have the same user name
     */
    @Override
    public boolean equals(Object obj){
        if(obj instanceof User){
            User user=(User)obj;
             if(this.userName.equals(user.getUserName()) && this.server.getName().equals(user.getServer().getName())){
                return true; 
             }
             return false;
            
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (this.userName != null ? this.userName.hashCode() : 0);
        hash = 47 * hash + (this.server != null ? this.server.hashCode() : 0);
        return hash;
    }
    
    /**
     * this method removes a cached user object from the user cache so
     * as to force the server to read fresh details of the user from the database
     * @param userName the name of the user we want to remove from the cache
     */
    public static void removeFromUserCache(String userName){
        userCache.remove(userName);
    }
    
    /*----------------private implementation---------------------*/
    
    
    private  void createUser(String userName, String pass, String host, Server serv,Double sup, String group, Action action) throws UserExistsException{
        if(group==null){
          group="unassigned";  
         }
        String nextRandom;
        String dbName=serv.getDatabase().getDatabaseName();
        UniqueRandom ur=new UniqueRandom(20);
        nextRandom = ur.nextRandom();
             // check to ensure the user_id is always unique
        while(Database.ifValueExists(nextRandom,"USERS","USER_ID",serv.getDatabase()) || Database.ifValueExists(nextRandom,"USER_HISTORY","USER_ID",serv.getDatabase())){
                nextRandom=ur.nextRandom();  
              }
        if(Database.ifValueExists(userName,"USERS", "USER_NAME",serv.getDatabase())){
                    throw new UserExistsException();
                  }
                 this.systemUserId=nextRandom;
                 try{
                    byte[] bytes=Security.makePasswordDigest(userName,pass.toCharArray());
                    String passw=Security.toBase64(bytes);
                     Long time=System.currentTimeMillis();
                     Database.executeQuery("INSERT INTO USERS VALUES(?,?,'"+passw+"',?,NOW(),0,0,?,?,NOW(),?,?)",dbName,nextRandom,userName,host,time.toString(),sup.toString(),group,action.getActionID());
                     action.saveAction();
                     Logger.toConsole("new user created", this.getClass());
                   
                  }
                 catch(Exception e){
                    
                 }
    }
    
    
    private  void setSuperiority(boolean updateMode,PermanentPrivilege... priv){
           Double sup = this.getUserSuperiority();
           int size=1;
            HashMap list=new HashMap();
            for(int y=0; y<priv.length; y++){
              list.put(priv[y].getName(),priv[y]); 
            }
            HashMap userPrivileges = this.getUserPrivileges();
            sup=sup*userPrivileges.size();
            if(updateMode==true){
               list.keySet().removeAll(userPrivileges.keySet());
               size=list.size()+userPrivileges.size();
              }
             else if(updateMode==false){
               list.keySet().retainAll(userPrivileges.keySet());
               size=userPrivileges.size()-list.size();
             
              }

            Iterator iter = list.values().iterator();
             for(;iter.hasNext();){
               try{
                int groupLevel = ((ResourceGroup)iter.next()).getGroupLevel();
                  if(updateMode==true){
                     sup=sup+groupLevel;   
                  }
                  else{
                   sup=sup-groupLevel;    
                  }

                 }
                 catch(Exception e){
                   Logger.toConsole(e, User.class);
                 }
             }
              if(sup==0.0){
                this.setUserSuperiority(0.0);     
              }
              else{
                 this.setUserSuperiority(sup/size);  
              }
            
    }
    
    private static Double assignSuperiority(PermanentPrivilege...priv){
        if(priv.length==0){
          return 0.0;  
        }
       Double sup=0.0;
        for(int x=0; x<priv.length; x++){
            Integer level = priv[x].getGroupLevel();
             sup=sup+level;
        }
      return sup/priv.length;
    }
    
    
    private void setHost(String host){
       this.hostName=host; 
    }
    private void setPass(String pass){
        this.pass=pass;
    }
    
    private void setSuperiority(double sup){
        this.superiority=sup;
    }
    
    private void setGroup(String group){
        this.group=group;
    }
    
    private void setUserId(String userId){
        this.systemUserId=userId;
    }
    
    private void setUserPrivileges(HashMap priv){
       this.priv=priv; 
    }
    /*-------------------end private implementation--------------*/
    
   
}

