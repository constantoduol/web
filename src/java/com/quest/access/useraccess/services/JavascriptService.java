
package com.quest.access.useraccess.services;

import com.quest.access.useraccess.services.annotations.Endpoint;
import com.quest.access.useraccess.services.annotations.WebService;
import com.quest.access.common.mysql.Database;
import com.quest.access.control.Server;
import com.quest.access.useraccess.Serviceable;
import com.quest.servlets.ClientWorker;
import java.sql.ResultSet;
import java.util.logging.Level;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author Conny
 * @version 15th Oct 2012
 * service_name = js_service
 * standard messages include
 * <ol>
 *   <li>query: used to query the database, parameters for this message are the database name and the sql to run</li>
 *   
 * </ol>
 */
@WebService (name = "js_service", level = 10, privileged = "yes")
public class JavascriptService implements Serviceable{

    @Override
    public void onCreate() {

    }

    @Override
    public void onStart() {

    }
    
 
  
    @Endpoint(name="query")
    public void queryDatabase(ClientWorker worker,Server serv){
        JSONObject query=worker.getRequestData();
        String dbName = (String)query.opt("db_name");
        String sql=(String)query.opt("sql");
        try {
         ResultSet set = Database.executeQuery(sql, dbName);
         if(sql.toUpperCase().indexOf("INSERT")>-1){
            worker.setResponseData("success");
            serv.messageToClient(worker);
            //worker, msg
            return;
        }
            String [] labels=new String[set.getMetaData().getColumnCount()+1];
            JSONObject json=new JSONObject();
            for(int x=1; x<labels.length; x++){
               labels[x] = set.getMetaData().getColumnLabel(x); 
                try {
                    json.put(labels[x],new JSONArray());
                } catch (JSONException ex) {

                }
              }
               while(set.next()){
                 for(int x=1; x<labels.length; x++){
                 String value = set.getString(x);
                 ((JSONArray)json.get(labels[x])).put(value);
               }  
           }
           worker.setResponseData(json);
           serv.messageToClient(worker);
        } catch (Exception ex) {
            worker.setResponseData(ex);
            serv.exceptionToClient(worker);
            System.out.println(ex);
        }
    }

    @Override
    public void service() {
       
    }
    
   
    
}
