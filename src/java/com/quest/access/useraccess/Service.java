
package com.quest.access.useraccess;

import com.quest.access.common.ExtensionClassLoader;
import com.quest.access.common.UniqueRandom;
import com.quest.access.common.mysql.Database;
import com.quest.access.control.Server;
import com.quest.access.useraccess.verification.Action;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author constant oduol
 * @version 1.0(10/5/12)
 */


/**
 * <p>
 * This file defines a framework for providing services through a server
 * dividing the activities of a server into services splits up the servers
 * work and ensures that the logic of the different services is easily maintained
 * in different class files. When a programmer writes a service class he initializes
 * the service by creating an instance of the service class with the specified details.
 * When the server starts it reads all the services registered for this server into memory
 * and therefore whenever a client requests for any service, the server checks whether the service
 * exists, if it exists the server gets the location of the service class and then invokes the
 * provideService method in the specific service class.
 * </p>
 * <p>
 * Service providing classes must implement the Serviceable interface and provide an
 * implementation for the provideService() method. Services normally have a permanent 
 * privilege associated with them and therefore if a user has access to a service it
 * means he has the corresponding permanent privilege, revoking this privilege from
 * the user renders him unable to access the corresponding service. Every client's request
 * to a service runs in a different thread which invokes the specified service and returns the 
 * result to the client, this means that service classes should be designed with multi threading
 * in mind since different threads may call a service class at the same time
 * </p>
 * 
 */
public class Service {
    private static ConcurrentHashMap<String,Service> serviceCache=new ConcurrentHashMap();
    /**
     * this is the unique id of a service
     */
    
    private String serviceId;
    /**
     * this is the name of the service
     */
    private String name;
    /**
     * the permanent privilege associated with this service
     */
    private PermanentPrivilege priv;
    
    /**
     * this is the class that has the implementation of this service
     */
    private Class serviceClass;
    
    
    
    /**
     * this constructs a service object and stores the location and name of the service in the 
     * SERVICES table in the specific server's database
     * @param name the name of the service, this is the name the clients use when requesting for a
     *        service
     * @param serviceClass this is the class with the logic for the service so if a client makes a
     *        request for this service the code in this class is called
     * @param serv this is the server in whose scope this service is defined, therefore a different
     *        server cannot recognize this service because it does not belong to it
     */
   public Service(String name, Class serviceClass, Server serv,int level, Action action) throws ServiceExistsException{
        if(Database.ifValueExists(name,"SERVICES","SERVICE_NAME", serv.getDatabase())){
            // service already exists
            throw new ServiceExistsException();
         }
         UniqueRandom ur=new UniqueRandom(10);
         String serviceID=ur.nextRandom();
         while(Database.ifValueExists(serviceID,"SERVICES", "SERVICE_ID", serv.getDatabase())){
             serviceID=ur.nextRandom();
          }
         this.serviceId=serviceID;
         this.serviceClass=serviceClass;
         String dbName=serv.getDatabase().getDatabaseName();
         Database.executeQuery("INSERT INTO SERVICES VALUES(?,?,?,NOW(),?)",dbName, serviceID,name,serviceClass.getName(),action.getActionID());
         action.saveAction();
         PermanentPrivilege privilege;
        try {
          privilege = new PermanentPrivilege(name, level,serv,action);
          this.priv=privilege;
          Resource res=new Resource(Serviceable.class);
          priv.initialize(new Resource[]{res});
          runOnCreate();
        } catch (Exception ex) {
          System.out.println(ex);     
        }
         //refresh this server's services
         serv.refreshServices();
         System.out.println("Service "+name+" created");
         serv.startService(serviceClass.getName());
   }
   
   public void runOnCreate(){
        try {
            Method method = serviceClass.getMethod("onCreate", (Class[]) null);
            Object newInstance = serviceClass.newInstance();  
            method.invoke(newInstance, (Object[]) null);
        } catch (Exception ex) {
            Logger.getLogger(Service.class.getName()).log(Level.SEVERE, null, ex);
        } 
   }
   
   public void runOnStart(){
         try {
            Method method = serviceClass.getMethod("onStart", (Class[]) null);
            Object newInstance = serviceClass.newInstance();  
            method.invoke(newInstance, (Object[]) null);
        } catch (Exception ex) {
            Logger.getLogger(Service.class.getName()).log(Level.SEVERE, null, ex);
        }   
   }
   
   private Service(String name, PermanentPrivilege priv, Class clazz, String servId){
       this.name=name;
       this.priv=priv;
       this.serviceClass=clazz;
       this.serviceId=servId;
   }
   
   
   /**
    * this method returns the permanent privilege associated with a service
    */
   public PermanentPrivilege getServicePrivilege(){
       return this.priv;
   }
   
   
   /**
    * this method returns the unique system generated id for this service
    */
   
   public String getServiceId(){
       return this.serviceId;
   }
   /**
    * this method returns the class that has an implementation for this service
    */
   public Class getServiceClass(){
       return this.serviceClass;
   }
   /**
    * this method returns the name of this service
    */
   
   public String getServiceName(){
       return this.name;
   }
   
   

   

   
   /**
    * this method assigns a service to a groups of users
    * @param groups this represents the groups of users to be assigned this service
    * @param service this is the service to be assigned to the users
    * @param serv this is the server on which the service is accessed from
    */
   public static void assignServiceToGroups(ArrayList groups, Service service, Server serv){
       PermanentPrivilege.assignPrivilegeToGroups(groups,service.getServicePrivilege(), serv);
   }
   
   
   /**
    * this method returns an instance of an existing service
    * @param serviceName the name the service was given when it was created
    * @param serv the server the service was created in
    */
   
    public static Service getExistingService(String serviceName, Server serv) throws NonExistentServiceException {
           if(serviceCache.containsKey(serviceName)){
               return serviceCache.get(serviceName);
              }
              String name="";
              String serviceLocation="";
              String serviceID="";
              Class serviceClazz=null;
              String dbName=serv.getDatabase().getDatabaseName();
              ResultSet set = Database.executeQuery("SELECT * FROM SERVICES WHERE SERVICE_NAME=?", dbName, serviceName);
             
             try{
                    while(set.next()){
                        name=set.getString("SERVICE_NAME"); 
                        serviceLocation=set.getString("SERVICE_LOCATION");
                        serviceID=set.getString("SERVICE_ID");
                      }  
                
                   if(name.equals(serviceName)){
                       PermanentPrivilege privilege = PermanentPrivilege.getPrivilege(serviceName, serv);
                      try{
                         serviceClazz=Class.forName(serviceLocation);
                      }
                      catch(ClassNotFoundException e){
                        serviceClazz=Class.forName(serviceLocation,false,new ExtensionClassLoader(serv.getExtensionDir()));  
                      }
                       Service serviz=new Service(name, privilege, serviceClazz,serviceID);
                       serviceCache.put(name, serviz);
                       return serviz;
                        
                    }
                  else{
                     throw new NonExistentServiceException();
                  }
                
             }
           catch(Exception e){
               throw new NonExistentServiceException();
           }
      }
    
    /**
     * this method deletes a service completely and the privilege associated with it
     * and if any users were assigned this privilege the privilege is revoked
     * @param serviceName the name of the service to delete
     * @param serv the server the service was originally created in
     */
    public static void deleteService(String serviceName, Server serv) throws NonExistentPermanentPrivilegeException{
        String dbName=serv.getDatabase().getDatabaseName();
         Database.executeQuery("DELETE FROM SERVICES WHERE SERVICE_NAME=?",dbName,serviceName);
          PermanentPrivilege.deletePrivilege(serviceName, serv);
         removeFromCache(serviceName);
      }
    
    /**
     * this method removes the service mapped by the specified service name
     * from the service cache
     * @param name the name of the service to be removed from the cache
     */
    public static void removeFromCache(String name){
        serviceCache.remove(name);
    }
    
    /**
     * this method empties all cached services
     */
    public static void emptyServiceCache(){
       serviceCache.clear();   
    }
    
    
   
   
   }
   

