package com.quest.access.useraccess.services;

import com.quest.access.useraccess.services.annotations.Endpoint;
import com.quest.access.useraccess.services.annotations.WebService;
import com.quest.access.common.ExtensionClassLoader;
import com.quest.access.common.Logger;
import com.quest.access.common.mysql.Database;
import com.quest.access.control.Server;
import com.quest.access.useraccess.PermanentPrivilege;
import com.quest.access.useraccess.Resource;
import com.quest.access.useraccess.Service;
import com.quest.access.useraccess.Serviceable;
import com.quest.access.useraccess.services.annotations.Model;
import com.quest.access.useraccess.services.annotations.Models;
import com.quest.access.useraccess.verification.UserAction;
import com.quest.servlets.ClientWorker;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author constant oduol
 * @version 1.0(23/6/12)
 */

/**
 * this class provides the core services for managing privileges on a quest access server
 * in order for a user to access this service he must be granted the necessary privileges.
 * The privilege to this service should be assigned to administrators only. The privilege
 * to this service is automatically assigned to the root user and he can then assign this
 * privilege to other users
 * The following messages are the ones understood by this service
 *   <ol>
 *    <li>create_privilege : this message tells the server to create a new privilege</li>
 *    <li>delete_privilege : this message tells the server to delete the specified privilege</li>
 *    <li>view_privilege : this message tells the server to send all the details concerning this privilege</li>
 *    <li>grant_to_group : this message tells the server to grant the specified privilege to a specific user group</li>
 *    <li>revoke_from_group : this message tells the server to revoke a specified privilege from a specific user group</li>
 *    <li>load_privilege : this message tells the server to load a new privilege from an xml file</li>
 *    <li>create_service : this message tells the server to create a new service</li>
 *    <li>delete_service : this message tells the server to delete a service</li>
 *    <li>load_service : this message tells the server to load a service from an xml file</li>
 *    <li>all_privilege_names: this message is sent to request for the names of all privileges</li>
 *    <li>disable_privilege: this message is sent to disable a given privilege</li>
 *    <li>enable_privilege: this message is sent to enable a given privilege</li>
 *   </ol>
 * 
 */

@WebService (name = "privilege_service", level = 10, privileged = "yes")
@Models(models = {
     @Model(
       database = "user_server", table = "PRIVILEGES", 
       columns = {
                  "USER_ID VARCHAR(20)",
                  "GROUP_ID VARCHAR(256)"
               }
        ),
      @Model(
       database = "user_server", table = "RESOURCE_GROUPS", 
       columns = {"GROUP_ID VARCHAR(20) PRIMARY KEY",
                  "NAME VARCHAR(256)",
                  "LEVEL INT",
                  "ACCESS_STATE BOOL",
                  "CREATED DATETIME",
                  "ACTION_ID VARCHAR(60)"
               }
        ),
     @Model(
       database = "user_server", table = "RESOURCES", 
       columns = {"RESOURCE_ID VARCHAR(20) PRIMARY KEY",
                  "GROUP_ID VARCHAR(20)",
                  "NAME VARCHAR(256)",
                  "CREATED DATETIME"
               }
        ),
     @Model(
       database = "user_server", table = "SERVICES", 
       columns = {"SERVICE_ID VARCHAR(20) PRIMARY KEY",
                 "SERVICE_NAME VARCHAR(20)",
                 "SERVICE_LOCATION VARCHAR(256)",
                 "CREATED DATETIME",
                 "ACTION_ID VARCHAR(60)"
               }
        ),
     @Model(
       database = "user_server", table = "PRESET_GROUPS", 
       columns = {"PRESET_ID VARCHAR(20)",
                  "PRESET_NAME VARCHAR(20)",
                  "GROUP_NAME VARCHAR(50)",
                  "CREATED DATETIME",
                  "ACTION_ID VARCHAR(60)"
               }
        ),
       @Model(
       database = "user_server", table = "PRESET_PRIVILEGES", 
       columns = {"PRESET_ID VARCHAR(20)",
                  "PRIVILEGE_ID VARCHAR(20)"
               }
        )
     }
)
public class PrivilegeService implements Serviceable {
   
   @Endpoint(name="create_privilege")
    public void createPrivilege(Server serv, ClientWorker worker){
        JSONObject details=worker.getRequestData();
        PermanentPrivilege priv;
        Resource [] res;
        try{
           String name = (String)details.optString("name"); 
           Integer level=Integer.parseInt((String)details.optString("level"));
           UserAction action=new UserAction(serv, worker, "CREATE_PRIVILEGE "+name+"");
           ArrayList resources=(ArrayList)details.get("resources");
           res=new Resource[resources.size()];
           for(int x=0; x<resources.size(); x++){
              try{
                 res[x]=new Resource(Class.forName((String)resources.get(x)));
              }
              catch(ClassNotFoundException e){
                 res[x]=new Resource(Class.forName((String)resources.get(x),false, new ExtensionClassLoader(serv.getExtensionDir())));  
              }
            }
           priv=new PermanentPrivilege(name, level,serv,action);
           priv.initialize(res);
           worker.setResponseData(Message.SUCCESS);
           serv.messageToClient(worker);
           
          }
           catch(Exception e){
             worker.setResponseData(e);
             serv.exceptionToClient(worker);
           }
       }
   
   
   
    
    @Endpoint(name="delete_privilege")
    public  void deletePrivilege(Server serv,ClientWorker worker){
        JSONObject requestData = worker.getRequestData();
        String name = requestData.optString("name");
        try {
            SecurityException sExp=new SecurityException("Cannot delete core privileges");
            if("user_service".equals(name) || "privilege_service".equals(name)){
              worker.setResponseData(sExp);
              serv.exceptionToClient(worker);
              return;
            }
           // Service.deleteService(name, serv);
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
            UserAction action=new UserAction(serv,worker,"DELETE_PRIVILEGE "+name+"");
            action.saveAction();
        } catch (Exception ex) {
           worker.setResponseData(ex);
           serv.exceptionToClient(worker);
        }

    }
    
    
    
    @Endpoint(name="view_privilege")
    public  void viewPrivilege(Server serv, ClientWorker worker){
        JSONObject requestData=worker.getRequestData();
        String name=requestData.optString("name");
        try {
           PermanentPrivilege privilege = PermanentPrivilege.getPrivilege(name, serv);
           int lvl= privilege.getGroupLevel();
           boolean state=privilege.getAccessState();
           Date date=privilege.getCreationTime();
           String id=privilege.getPermanentPrivilegeID();
           JSONObject details=new JSONObject();
            try {
                details.put("level", lvl);
                details.put("state", state);
                details.put("created", date.toString());
                details.put("id", id);
                
                
            } catch (JSONException ex) {

            }
           worker.setResponseData(details);
           serv.messageToClient(worker);
        } catch (Exception ex) {
           worker.setResponseData(ex);
           serv.exceptionToClient(worker);
        }
        
       }
    
    
    
    @Endpoint(name="grant_to_group")
    public  void grantPrivilegeToGroup(Server serv, ClientWorker worker){
        try {
           JSONObject details=worker.getRequestData();
           String group=details.optString("group");
           String privName=details.optString("privilege");
           ArrayList groups=new ArrayList();
           groups.add(group);
           PermanentPrivilege privilege = PermanentPrivilege.getPrivilege(privName, serv);
           PermanentPrivilege.assignPrivilegeToGroups(groups, privilege, serv);
           worker.setResponseData(Message.SUCCESS);
           serv.messageToClient(worker);
           UserAction action=new UserAction(serv, worker, "GRANT_PRIVILEGE_TO_GROUP "+groups.get(0) +"");
           action.saveAction();
        } catch (Exception ex) {
          worker.setResponseData(ex);
          serv.exceptionToClient(worker);
        }

    }
    
    
    @Endpoint(name="revoke_from_group")
    public  void revokePrivilegeFromGroup(Server serv, ClientWorker worker){
        try {
          JSONObject details=worker.getRequestData();
          String group=details.optString("group");
          String privName=details.optString("privilege");
          SecurityException sExp=new SecurityException("group root cannot have specified privileges revoked");
          if(("user_service".equals(privName) || "privilege_service".equals(privName)) && group.equals("root") ){
              worker.setResponseData(sExp);
             serv.exceptionToClient(worker);
          }
           PermanentPrivilege privilege;
           privilege = PermanentPrivilege.getPrivilege(privName, serv);
           ArrayList groups=new ArrayList();
           groups.add(group);
           PermanentPrivilege.revokePrivilegeFromGroups(groups, privilege, serv);
           worker.setResponseData(Message.SUCCESS);
           serv.messageToClient(worker);
           UserAction action=new UserAction(serv,worker, "REVOKE_PRIVILEGE_FROM_GROUP "+groups.get(0) +"");
           action.saveAction();
        } catch (Exception ex) {
           worker.setResponseData(ex);
          serv.exceptionToClient(worker);
        }

    }
    
    
    
   
    
    
    
    @Endpoint(name="create_service")
    public  void createService(Server serv, ClientWorker worker){
        try {
            JSONObject details=worker.getRequestData();
            Class location=null;
            String name=details.optString("name");
            Integer level=Integer.parseInt(details.optString("level"));
            try{
               location=Class.forName(details.optString("clazz"));
             }
             catch(ClassNotFoundException e){
               try {
                  location=Class.forName(details.optString("clazz"),false, new ExtensionClassLoader(serv.getExtensionDir()));
                }
                catch(Exception ex){
                   worker.setResponseData(ex);
                  serv.exceptionToClient(worker);
                }
             }
            UserAction action=new UserAction(serv, worker, "CREATE_SERVICE "+name);
            Service service=new Service(name, location, serv, level,action);
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
            
        } catch (Exception ex) {
            worker.setResponseData(ex);
           serv.exceptionToClient(worker);
        }
    }
    
    
    
    @Endpoint(name="delete_service")
    public  void deleteService(Server serv,ClientWorker worker){
      JSONObject requestData = worker.getRequestData();
      String name=requestData.optString("name");
      try {
            SecurityException sExp=new SecurityException("Cannot delete core services");
            if("user_service".equals(name) || "privilege_service".equals(name)){
              worker.setResponseData(sExp);
              serv.exceptionToClient(worker);
              return;
            }
           // Service.deleteService(name, serv);
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
            UserAction action=new UserAction(serv,worker,"DELETE_SERVICE "+name+"");
            action.saveAction();
        } catch (Exception ex) { 
           worker.setResponseData(ex);
           serv.exceptionToClient(worker);
        }
    }
   
    
  
    
    
    
    
    @Endpoint(name="all_privilege_names")
    public  void getAllPrivilegeNames(Server serv, ClientWorker worker){
            String dbName=serv.getDatabase().getDatabaseName();
            ResultSet set = Database.executeQuery("SELECT * FROM RESOURCE_GROUPS", dbName);
            ArrayList names=new ArrayList();
            try {
              while(set.next()){
               names.add(set.getString("NAME"));
            }
           worker.setResponseData(names);
           serv.messageToClient(worker);
        } catch (SQLException ex) {
          
        }
    }
    
    @Endpoint(name="disable_privilege")
    public  void disablePrivilege(Server serv, ClientWorker worker){
       JSONObject requestData = worker.getRequestData();
       String name=requestData.optString("name");
        try {
            PermanentPrivilege privilege = PermanentPrivilege.getPrivilege(name, serv);
            privilege.setAccessState(false);
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
            UserAction action=new UserAction(serv,worker, "DISABLE_PRIVILEGE "+name+"");
            action.saveAction();
        } catch (Exception ex) {
           worker.setResponseData(ex);
           serv.exceptionToClient(worker);
        }
    }
    
    
    
    
    @Endpoint(name="enable_privilege")
    public  void enablePrivilege(Server serv, ClientWorker worker){
       JSONObject requestData = worker.getRequestData();
       String name=requestData.optString("name");
        try {
            PermanentPrivilege privilege = PermanentPrivilege.getPrivilege(name, serv);
            privilege.setAccessState(true);
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
            UserAction action=new UserAction(serv,worker, "ENABLE_PRIVILEGE "+name+"");
            action.saveAction();
        } catch (Exception ex) {
           worker.setResponseData(ex);
           serv.exceptionToClient(worker);
        }
    }
    
    
    
    @Endpoint(name="all_user_groups")
    public  void getAllUserGroups(Server serv,ClientWorker worker){
         String dbName=serv.getDatabase().getDatabaseName();
          ResultSet set = Database.executeQuery("SELECT DISTINCT GROUPS FROM USERS", dbName);
            JSONArray values=new JSONArray();
             try {
              while(set.next()){
                String val = set.getString("GROUPS");
                if(!"root".equals(val)){
                  values.put(val);  
                }
            }
          worker.setResponseData(values);
          serv.messageToClient(worker);
        } catch (SQLException ex) {
          Logger.toConsole(ex, this.getClass());
        }
          
     }
    
    
    
    /*
    @Endpoint(name="view_service")
    public  void viewService(Server serv,ClientWorker worker){
       JSONObject requestData = worker.getRequestData();
       String name=requestData.optString("name");
        try {
           Service service = Service.getExistingService(name, serv);
           String id= service.getServiceId();
           String clazz=service.getServiceClass().getName();
           JSONObject details=new JSONObject();
            try {
                details.put("id", id);
                details.put("clazz", clazz);
                
            } catch (JSONException ex) {
              System.out.println(ex);
            }
            worker.setResponseData(details);
            serv.messageToClient(worker);
        } catch (Exception ex) {
           worker.setResponseData(ex);
           serv.exceptionToClient(worker);
        }
        
       }
    */
   
   
    
    
   
    @Override
    public void onCreate() {

    }

    @Override
    public void onStart() {

    }

    @Override
    public void service() {
       
    }
    
}
