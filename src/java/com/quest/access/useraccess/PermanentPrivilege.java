
package com.quest.access.useraccess;

import com.quest.access.common.Logger;
import com.quest.access.common.UniqueRandom;
import com.quest.access.common.mysql.Database;
import com.quest.access.control.Server;
import com.quest.access.useraccess.verification.Action;
import com.quest.access.useraccess.verification.UserAction;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;


/**
 *
 * @author constant oduol
 * @version 1.0(4/1/2012)
 */

/**
 * <p>
 * A permanent privilege is a privilege that can be assigned to 
 * a user over a long period of time without being revoked. Permanent privileges
 * define what a user has access to and what  a user does not have access to.
 * </p>
 * <p>
 * Permanent privileges are created by specifying the name of the privilege, the level of the privilege
 * (This is how superior this privilege is to others, developers can have there own measure of
 * superiority, a simple way would be to say the least superior privilege has a level of 1 while
 * the most superior has a level of 10) and the server in which this privilege is defined.
 * When these privileges are created they are stored in a table called RESOURCE_GROUPS in the
 * server's database, each privilege has a unique id which is used when assigning a privilege to
 * a user
 * </p>
 * <p>
 * Permanent privileges have resources associated with them. Every permanent privilege
 * has a number of resources associated with it, therefore if a user has access to a given permanent
 * privilege then he has access to all the resources under that permanent privilege.Normally the user's 
 * permanent privileges are retrieved at login time and stored in his or her session such that the server's 
 * privilegeHandler is able to determine whether a user should access a server resource or not. When a user
 * tries to access a permanent privilege that he has no access to a SecurityException is thrown by the server
 * Privileges can be loaded to a server via an xml file
 * </p>
 * @see com.qaccess.useraccess.Resource
 */

public class PermanentPrivilege extends ResourceGroup implements java.io.Serializable {
    /**
     * cache for permanent privileges
     */
    private static ConcurrentHashMap privilegeCache=new ConcurrentHashMap();
    /**
     * this is the unique system generated id for this privilege
     */
    private String privilegeID;
    /**
     * this tells us whether the resources in this permanent privilege can be accessed or not
     */
    private boolean isAccessible;
    
    private Server serv;
    
    private Date created;
    /**
     * creates a permanent privilege object that is accessible by users who have been
     * assigned this privilege
     * @param name the name of the permanent privilege 
     * @param level this shows how superior this privilege is in comparison
     *              to other privileges
     * @param serv  the server in which this privilege is to be created
     */
    public PermanentPrivilege(String name, int level, Server serv, Action action) throws PermanentPrivilegeExistsException{
        super(name,level);
        this.isAccessible=true;
        this.serv=serv;
        savePrivilege(this,action);
    }
    
    /**
     * creates a permanent privilege object while allowing the ability to 
     * specify whether the privilege is accessible or not
     * @param name the name of the privilege to be created
     * @param level this shows how superior this privilege is in comparison to
     *             other privileges
     * @param serv the server in which  this privilege is to be created
     * @param accessState tells us whether the privilege is accessible or not
     */
    private PermanentPrivilege(String name,int level, Server serv, boolean accessState, String privID, Date created){
        super(name,level);
        this.isAccessible=accessState;
        this.serv=serv;
        this.privilegeID=privID;
        this.created=created;
    }
    
    
    
    /**
     * this method initializes resources as belonging to a specific permanent privilege
     * @param group the permanent privilege we want to initialize with the specific resources
     * @param res the resources we want to set as belonging to this permanent privilege
     * @param serv the server in which this permanent privilege belongs
     * @return returns a permanent privilege object with the resources belonging to it
     *         having been saved
     */
    public static PermanentPrivilege initialize(PermanentPrivilege group, Resource[] res, Server serv){
         group.addManyResources(res);
         saveResources(group, serv);
         return group;
    }
    
    /**
     *this method changes the access state of this permanent privilege to either true or false
     * this determines whether this privilege is accessible or not
     * @param state if state is true then it means that access to this privilege is allowed
     *              otherwise if it is false then access to the privilege will be denied
     */
    public void setAccessState(boolean state){
      Integer toSet= state==true ? 1 : 0;
      // if someone is trying to disable this privilege then this
      // will force the server to refresh the privileges details in the database
      // and a call to getPrivilege() will cause a database read
      if(state==false){
         privilegeCache.remove(serv); 
         }
       Database.setValue(this.serv,"RESOURCE_GROUPS", "ACCESS_STATE", toSet.toString(), "NAME='"+this.getName()+"'");
       this.isAccessible=state;
    }
    
    /**
     * this method tells us whether this permanent privilege is accessible or not
     * it returns true if the permanent privilege is accessible and false if it is not
     */
    public boolean getAccessState(){
        return this.isAccessible;
    }
    
    /**
     * this method saves the resources specified under this permanent privilege
     * @param res the array of resources we want to initialize under this privilege
     */
    public PermanentPrivilege initialize(Resource [] res){
        this.addManyResources(res);
        saveResources(this, this.serv);
        return this;
    }
    
    /**
     * this method returns an instance of an existing privilege from the
     * specified server
     * @param privName the name of the privilege we want to get an instance of
     * @param serv the server this privilege is found
     * @return an instance of the existing privilege
     */
    public static PermanentPrivilege getPrivilege(String privName, Server serv) throws NonExistentPermanentPrivilegeException{
       boolean containsKey = privilegeCache.containsKey(privName);
         if(!containsKey){
            return PermanentPrivilege.getExistingPermanentPrivilege(privName, serv); // i need to get an existing permanent privilege
            }
           else{
          return (PermanentPrivilege)privilegeCache.get(privName);
         }
    }
    
    /**
     * this method initializes the permanent privilege specified by group by creating
     * new resource objects from the array of classes, this resources are then saved as being under
     * the permanent privilege specified by group
     * @param group this is the permanent privilege that is being initialized with the resource objects
     *              created from the class objects specified by cls
     * @param cls   this is an array of class objects representing interfaces, these objects are wrapped into
     *              resource objects and initialized under the specified permanent privilege
     * @param serv  the server that this permanent privilege exists
     */
    public static PermanentPrivilege initialize(PermanentPrivilege group, Class[] cls, Server serv){
        // we only intialize interfaces
        for(int x=0; x<cls.length; x++){
            if(cls[x].isInterface()){
                Resource res=new Resource(cls[x]);
               group.addResource(res);
            }
            else{
              throw new IllegalArgumentException(""+cls+" is not an interface");  
            }
        }
        saveResources(group, serv);
        return group;
    }
    
    /**
     * This method changes the name of a permanent privilege
     * @param newName the new name we want to call  a permanent privilege
     */
    @Override
    public void setName(String newName){
       Database.setValue(serv, "RESOURCE_GROUPS", "NAME", newName, "GROUP_ID='"+this.privilegeID+"'");
       privilegeCache.remove(this.getName());
       super.setName(newName);
    }
    
    /**
     * this method changes the level of this permanent privilege
     * @param level the new level we want to assign to this privilege
     */
    
    @Override
    public void setGroupLevel(Integer level){
       Database.setValue(serv, "RESOURCE_GROUPS", "LEVEL", level.toString(), "GROUP_ID='"+this.privilegeID+"'"); 
       privilegeCache.remove(this.getName());
       super.setGroupLevel(level);
    }
    
    /**
     * this method returns the system generated id of a permanent privilege
     */
    public String getPermanentPrivilegeID(){
        return this.privilegeID;
    }
    
    /**
     * 
     * @return the time when this privilege was created
     */
    public Date getCreationTime(){
        return this.created;
    }
    
    /**
     * this method gets an instance of an existing permanent privilege
     */
    private static PermanentPrivilege getExistingPermanentPrivilege(String name, Server serv) throws NonExistentPermanentPrivilegeException{
        String nam="";
        int level=0;
        String privID="";
        boolean accState=true;
        Date created=null;
        String dbName=serv.getDatabase().getDatabaseName();
        ResultSet set = Database.executeQuery("SELECT * FROM RESOURCE_GROUPS WHERE NAME=? ",dbName,name);
        try{
           while(set.next()){
                nam = set.getString("NAME");
                level = set.getInt("LEVEL");
                accState=set.getBoolean("ACCESS_STATE");
                privID=set.getString("GROUP_ID");
                created=set.getDate("CREATED");
               }
          if(nam.equals(name)){
               PermanentPrivilege pp=new PermanentPrivilege(name, level,serv,accState, privID,created);
               serv.addToPrivilegeList(pp);
               privilegeCache.put(name,pp);
               return pp;
             }
             set.close();
             throw new NonExistentPermanentPrivilegeException();
          }
         catch(Exception e){
           throw new NonExistentPermanentPrivilegeException();
         }
    }
    
    
    
    
    /**
     * this method saves the resources in a static resource group
     */
    private static void saveResources(PermanentPrivilege group, Server serv){
      String dbName=serv.getDatabase().getDatabaseName();
      for(int x=0; x<group.size(); x++){
        UniqueRandom ur=new UniqueRandom(7);
         String rID = ur.nextRandom();
            ResultSet set = Database.executeQuery("SELECT GROUP_ID,NAME FROM RESOURCES WHERE GROUP_ID=? AND NAME=? ",dbName,group.getPermanentPrivilegeID(),(String)group.getMemberNames().get(x));
           try{
            while(set.next()){
                    String id = set.getString("GROUP_ID");
                    String name = set.getString("NAME");
                    if(name!=null && id!=null){
                        // this means they already exist
                        return;
                    }
              }
            set.close();
           }
            catch(Exception e){
              System.out.println(e);
           }
          while(Database.ifValueExists(rID, "RESOURCES", "RESOURCE_ID", serv.getDatabase())){
               rID=ur.nextRandom();
           }
          Database.executeQuery("INSERT INTO RESOURCES VALUES(?,?,?, NOW())",dbName,rID,group.getPermanentPrivilegeID(),(String)group.getMemberNames().get(x));
         System.out.println("Resources saved"); 
      }
    }
    
  
 
    
   
    
    private void savePrivilege(PermanentPrivilege priv, Action action) throws PermanentPrivilegeExistsException{
      String dbName=this.serv.getDatabase().getDatabaseName();
       UniqueRandom ur=new UniqueRandom(10);
         String privID = ur.nextRandom();
           while(Database.ifValueExists(privID, "RESOURCE_GROUPS", "GROUP_ID", this.serv.getDatabase())){
               privID=ur.nextRandom();
           }
           this.privilegeID=privID;
           created=new Date();
           if(Database.ifValueExists(priv.getName(),"RESOURCE_GROUPS","NAME", this.serv.getDatabase())){
               System.out.println("Privilege already exists");
              throw new PermanentPrivilegeExistsException();
           }
          Database.executeQuery("INSERT INTO RESOURCE_GROUPS"
                  + " VALUES(?,?,?,1, NOW(),?)",dbName,privID,priv.getName(),priv.getGroupLevel().toString(),action.getActionID());
          action.saveAction();
        System.out.println("Permanent privilege saved");
       this.serv.addToPrivilegeList(priv);
}
    
    /**
     * this method assigns a specific privilege to a group of
     * users defined in the arraylist groups, if the users of this groups have this 
     * privilege already, the privilege is not duplicated
     * @param groups the groups to be assigned the specified permanent privilege
     * @param priv the privilege to be assigned to the user groups
     * @param serv  the server to which this permanent privilege is being assigned in
     */
   public static void assignPrivilegeToGroups(ArrayList groups, PermanentPrivilege priv, Server serv){
      //check if service exists
       //then assign this privilege to each user whose group is specified
       String privID=priv.getPermanentPrivilegeID();
        String dbName=serv.getDatabase().getDatabaseName();
        ArrayList userNames=new ArrayList();
         for(int x=0; x<groups.size(); x++){
            ResultSet set=Database.executeQuery("SELECT USER_NAME FROM USERS WHERE GROUPS=?", dbName,(String)groups.get(x));
            try{
             while(set.next()){
                userNames.add(set.getString("USER_NAME")); 
              }
              set.close();
              saveGroupPrivilege(userNames,serv,priv);
            }
            catch(Exception e){
              System.out.println(e);
            }
       }
       
   }
   
   public static void revokePrivilegeFromGroups(ArrayList groups, PermanentPrivilege priv, Server serv){
       String dbName=serv.getDatabase().getDatabaseName();
        ArrayList userNames=new ArrayList();
        for(int x=0; x<groups.size(); x++){
          ResultSet set=Database.executeQuery("SELECT USER_NAME FROM USERS WHERE GROUPS=?", dbName,(String)groups.get(x));
            try{
             while(set.next()){
                userNames.add(set.getString("USER_NAME")); 
              }
              set.close();
              revokePrivilege(serv, userNames, priv);
            }
            catch(Exception e){
             System.out.println(e);
            }   
        }
   }
   
   
   
   private static void saveGroupPrivilege(ArrayList userNames, Server serv,PermanentPrivilege priv){
       for(int x=0; x<userNames.size(); x++){
            try {
                 User user=User.getExistingUser((String)userNames.get(x), serv);
                 user.grantPrivileges( priv);
            } catch (NonExistentUserException ex) {

            }
        }
   }
   
   private static void revokePrivilege(Server serv, ArrayList userNames, PermanentPrivilege priv){
      for(int x=0; x<userNames.size(); x++){
            try {
                User user=User.getExistingUser((String)userNames.get(x), serv);
                user.revokePrivileges( priv);
            } catch (NonExistentUserException ex) {
                Logger.toConsole(ex, PermanentPrivilege.class);
            }
      }
    }
   
   /**
    * this method completely deletes a privilege and no further reference can
    * be made to that privilege. if any users had been granted that privilege
    * it is revoked
    * @param privName the name of the privilege
    * @param serv  the server where the privilege was initially created
    */
   public static void deletePrivilege(String privName, Server serv) throws NonExistentPermanentPrivilegeException{
       String dbName=serv.getDatabase().getDatabaseName();
        PermanentPrivilege privilege = PermanentPrivilege.getPrivilege(privName, serv);
        String privID=null;
        ResultSet set = Database.executeQuery("SELECT GROUP_ID FROM "
                + "RESOURCE_GROUPS WHERE NAME=?", dbName, privName);
         try {
             
            while(set.next()){
              privID = set.getString("GROUP_ID");
            }
             set.close();
             if(privID==null){
                 return;
             }
          
            Database.executeQuery("DELETE FROM RESOURCE_GROUPS WHERE GROUP_ID=?", dbName, privID);
            Database.executeQuery("DELETE FROM RESOURCES WHERE GROUP_ID=?", dbName, privID);
           // Database.executeQuery("DELETE FROM PRIVILEGES WHERE GROUP_ID=?", dbName, privID);
            serv.removeFromPrivilegeList(privilege);
            //TODO make sure when a privilege is revoked the superiority of a user is updated
            ResultSet set1 = Database.executeQuery("SELECT USER_NAME FROM PRIVILEGES, "
                    + "USERS WHERE GROUP_ID=? AND USERS.USER_ID=PRIVILEGES.USER_ID",dbName,privID);
                while(set1.next()){
                    String userName=set1.getString("USER_NAME");
                    User user=User.getExistingUser(userName, serv);
                    user.revokePrivileges(privilege);
                }
            privilegeCache.remove(privName);
            set1.close();
        } catch (Exception ex) {
          System.out.println(ex);
        }
   }
   
   /**
    * empties all cached permanent privileges
    */
   public static void emptyPrivilegeCache(){
       privilegeCache.clear();
   }
}
