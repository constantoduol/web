
package com.quest.access.useraccess;

import com.quest.access.common.UniqueRandom;
import com.quest.access.common.mysql.Database;
import com.quest.access.control.Server;
import com.quest.access.useraccess.verification.UserAction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author constant oduol
 * @version 1.0(18/6/12)
 */

/**
 * a preset group is a way of easily creating users by putting together commonly assigned privileges
 * and services under one name, after doing this users can be created by assigning them to a preset group
 * instead of manually assigning single privileges and services one at a time.
 */
public class PresetGroup {
    /**
     * this is the server this preset group belongs to
     */
    private Server serv;
    /**
     * this is a cache to store data about preset groups
     */
    private static ConcurrentHashMap<String, PresetGroup> presetCache= new ConcurrentHashMap();
    /**
     * this is the unique system generated id for a preset group
     */
    private String presetID;
    
    /*
     * the name of the preset group
     */
    private String name;
    
    /*
     * this is when this preset group was created
     */
    private Date created;
    
    /*
     * the name of the group of users associated with this preset group
     */
    private String groupName;
    
    /*
     * the privileges associated with this preset group
     */
    
    private ArrayList privs;
    /**
     * this creates a preset group object 
     * @param name the name of the preset group, this is supposed to be unique
     * @param groupName the group of users associated with this preset group
     * @param serv the server where this preset group belongs
     * @param services the services associated with this preset group
     * @param priv the privileges associated with this preset group
     */
    public PresetGroup(String name,String groupName,Server serv,ArrayList privs,UserAction action) throws PresetGroupExistsException{
     this.name=name;
     this.groupName=groupName;
     this.privs=privs;
     this.serv=serv;
     this.created=new Date();
     savePresetGroup(serv, privs, action);
    }
    
    private PresetGroup(String name, String groupName, String presetID, ArrayList privileges,Server serv, Date created){
        this.name=name;
        this.groupName=groupName;
        this.presetID=presetID;
        this.privs=privileges;
        this.serv=serv;
        this.created=created;
    }
    /**
     * this method returns the unique system generated id for this
     * preset group
     */
    public String getPresetId(){
       return this.presetID; 
    }
    
    /**
     * this method returns the name of the preset group
     */
    
    public String getPresetGroupName(){
        return this.name;
    }
    /**
     * this method changes the name of this preset group
     * @param name the new name of the preset group
     */
    public void setPresetGroupName(String name){
        Database.setValue(this.serv, "PRESET_GROUPS", "PRESET_NAME", name, "PRESET_ID='"+this.presetID+"'");
        presetCache.remove(this.name);
        this.name=name;
    }
    /**
     * this method returns the name of the user group associated with this
     * preset group
     */
    public String getGroupName(){
        return this.groupName;
    }
    /**
     * this method changes the user group associated with this preset group
     * @param groupName the new group to be associated with this preset group
     */
    public void setGroupName(String groupName){
       Database.setValue(this.serv, "PRESET_GROUPS", "GROUP_NAME",groupName , "PRESET_ID='"+this.presetID+"'");
    }
    /**
     * this method returns permanent privileges of this preset group
     */
    public ArrayList getPermanentPrivileges(){
        return this.privs;
    }
    
    /**
     * 
     * @return the time this preset group was created
     */
    public Date getCreationTime(){
       return this.created; 
    }
    
    private void savePresetGroup(Server serv, ArrayList privs, UserAction action) throws PresetGroupExistsException{
     UniqueRandom ur=new UniqueRandom(10);
     String pr_id=ur.nextMixedRandom();
      this.presetID=pr_id;
      String dbName=serv.getDatabase().getDatabaseName();
       boolean exists=Database.ifValueExists(this.name,"PRESET_GROUPS","PRESET_NAME", serv.getDatabase());
         if(exists){
            throw new PresetGroupExistsException(); 
           }
          else{
             Database.executeQuery("INSERT INTO PRESET_GROUPS VALUES(?,?,?, NOW(),?)", dbName,pr_id,name,groupName,action.getActionID());
              action.saveAction();
              savePresetPrivileges(serv, pr_id, privs);
         } 
    }
    
    private void savePresetPrivileges(Server serv, String presetID, ArrayList privs){
        String dbName=serv.getDatabase().getDatabaseName();
          for(int x=0; x<privs.size(); x++){
            PermanentPrivilege priv=(PermanentPrivilege)privs.get(x);  
            String privID= priv.getPermanentPrivilegeID();
            Database.executeQuery("INSERT INTO PRESET_PRIVILEGES VALUES(?,?)",dbName, presetID,privID);
          }
     }
    
    
    
    /**
     * this method deletes the specified preset group and all its associations of privileges and
     * services
     * @param presetGroup the name of the preset group to be deleted
     * @param serv the server this preset group was initially created
     */
    public static void deletePresetGroup(String presetGroup ,Server serv){
        String dbName=serv.getDatabase().getDatabaseName();
        String preID="";
        ResultSet set = Database.executeQuery("SELECT PRESET_ID FROM "
                + "PRESET_GROUPS WHERE PRESET_NAME=?", dbName, presetGroup);
        try {
            while(set.next()){
               preID=set.getString("PRESET_ID");
             }
            set.close();
            if(preID!=null || preID.isEmpty()==false){
              Database.executeQuery("DELETE FROM PRESET_PRIVILEGES WHERE PRESET_ID=? ", dbName, preID);
              Database.executeQuery("DELETE FROM PRESET_GROUPS WHERE PRESET_ID=?", dbName,preID);
            }
            presetCache.remove(presetGroup);
        } catch (SQLException ex) {
        }

         
      }
    
    /**
     * this method gets an instance of an existing preset group and ensures persistence between 
     * newly created preset group objects and those object obtained from the database
     * @param name the name of the existing preset group
     * @param serv the server this preset group was originally created
     * @return the specified preset group is returned if it is existent otherwise an exception is thrown
     * @throws NonExistentPresetGroupException 
     */
    public static PresetGroup getExistingPresetGroup(String name, Server serv) throws NonExistentPresetGroupException{
            if(presetCache.containsKey(name)){
               return presetCache.get(name);
            }
         String dbName=serv.getDatabase().getDatabaseName();
         String preName="";
         String preID="";
         String groupName="";
         Date created=null;
         ArrayList privList=new ArrayList();
         ResultSet set = Database.executeQuery("SELECT * FROM PRESET_GROUPS WHERE PRESET_NAME=?",dbName, name);
           try {
            while(set.next()){
                preID=set.getString("PRESET_ID");
                preName=set.getString("PRESET_NAME");
                groupName=set.getString("GROUP_NAME");
                created=set.getDate("CREATED");
               }
              set.close();
            if(preName.equals(name)){
               ResultSet set1= Database.executeQuery("SELECT RESOURCE_GROUPS.NAME FROM "
                       + "PRESET_PRIVILEGES,RESOURCE_GROUPS, PRESET_GROUPS WHERE  "
                       + "RESOURCE_GROUPS.GROUP_ID=PRESET_PRIVILEGES.PRIVILEGE_ID AND"
                       + " PRESET_GROUPS.PRESET_ID=?",dbName, preID);
                      
                        while(set1.next()){
                         String privName=set1.getString("NAME");
                         try{
                         PermanentPrivilege privilege = PermanentPrivilege.getPrivilege(privName, serv);
                         privList.add(privilege);
                         }
                         catch(Exception e){
                             
                         }
                           }
                        set1.close();
                   PresetGroup group=new PresetGroup(name, groupName, preID, privList,serv,created);
                   presetCache.put(name, group);
                   return group;
                }
             else{
                 throw new NonExistentPresetGroupException();
             }
        } catch (SQLException ex) {
          throw new NonExistentPresetGroupException();
        }
    }
    
}


