/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.quest.extensions;

import com.quest.access.common.UniqueRandom;
import com.quest.access.common.io;
import com.quest.access.common.mysql.Database;
import com.quest.access.common.mysql.NonExistentDatabaseException;
import com.quest.access.control.Server;
import com.quest.access.useraccess.Serviceable;
import com.quest.access.useraccess.services.annotations.Endpoint;
import com.quest.access.useraccess.services.annotations.Model;
import com.quest.access.useraccess.services.annotations.Models;
import com.quest.access.useraccess.services.annotations.WebService;
import com.quest.servlets.ClientWorker;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author Connie
 */

@WebService (name = "class_service", level = 10, privileged = "no")
@Models(models = { //creates tables in the database
    @Model(
       database = "class_data", table = "STUDENT_DATA", 
       columns = {"ID VARCHAR(10) PRIMARY KEY", // ID : {ID1,ID2,ID3}
                  "NAME TEXT",                  // NAME : {NAME1,NAME2,NAME3}
                  "REGNO TEXT",
                  "CONTACT TEXT",
                  "TYPE TEXT",
                  "ATTENDANCE TEXT",
                  "CREATED LONG"
       })
    }
)
public class TestService implements Serviceable {
    
    private static Database db;
    
    private static final int minAttendance = 10;

    @Override
    public void service() {
      
    }

    @Override
    public void onCreate() {
        
    }

    @Override
    public void onStart() {
        try {
            //list,arraylist,hashmap,arrays
           //get an instance of the database
            db = Database.getExistingDatabase("class_data"); //get an instance of the database
        } catch (NonExistentDatabaseException ex) {
            //handle exception
            Logger.getLogger(TestService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    
    @Endpoint(name="save_attendance")
    public void saveAttendance(Server serv, ClientWorker worker){
      JSONObject data = worker.getRequestData();
      JSONArray ids = data.optJSONArray("ids"); //ids of students [id1,id2]
      JSONArray values  = data.optJSONArray("values"); // ["1#2#4","1#3#"]
      // 46466466 : 4,3,4,10,3,4
      for(int x = 0; x < ids.length(); x++){
          String id = ids.optString(x);
          //update the database with the new attendance
          String value = values.optString(x);
          db.query()
                  .update("STUDENT_DATA")
                  .set("ATTENDANCE='"+value+"'")
                  .where("ID='"+id+"'")
                  .execute(); //save the attendance
      }
      worker.setResponseData("success");
      serv.messageToClient(worker);
    }
    
    @Endpoint(name="create_student")
    public void createStudent(Server serv, ClientWorker worker){
       JSONObject data = worker.getRequestData();
       String name = data.optString("name");
       String regno = data.optString("regno");
       String contact = data.optString("contact");
       String type = data.optString("type");
       UniqueRandom rand = new UniqueRandom(10); //create a unique id for student
       String id = rand.nextMixedRandom();
       String att = "";
       Long time = System.currentTimeMillis();
       db.doInsert("STUDENT_DATA", new String[]{id,name,regno,contact,type,att,time.toString()}); //insert into database
       worker.setResponseData("success"); //send response to client
       serv.messageToClient(worker);
    }
    
    
    @Endpoint(name="delete_student")
    public void deleteStudent(Server serv, ClientWorker worker){
       JSONObject data = worker.getRequestData();
       String regno = data.optString("regno");
       db.query().delete().from("STUDENT_DATA").where("REGNO='"+regno+"'").execute();
       worker.setResponseData("success");
       serv.messageToClient(worker);
    }
    
  
    
    
    
    @Endpoint(name="read_attendance")
    public void readAttendance(Server serv,ClientWorker worker){
        try {
            //read the database
            JSONObject data = db.query()
                    .select("*")
                    .from("STUDENT_DATA")
                    .execute();
             //we interested in names and attendance

            JSONArray attendance = data.optJSONArray("ATTENDANCE"); //1#2#
            JSONArray names = data.optJSONArray("NAME");
            JSONArray realAttendance = new JSONArray();
            JSONArray ids = data.optJSONArray("ID");
          
            JSONArray allAttStatus = new JSONArray();
            for(int x = 0; x < ids.length(); x++){
               String att = attendance.optString(x);//1#2#
               StringTokenizer st = new StringTokenizer(att,"#"); //1#2#2 
               JSONArray studentAtt = new JSONArray();
               JSONArray singleAttStatus = new JSONArray();    //tells the status of attendance e.g met or not 
               while(st.hasMoreTokens()){
                  String finalAtt = st.nextToken();
                  Integer num = Integer.parseInt(finalAtt);
                  String attendStatus = "not qualified";
                  if(num >= minAttendance){
                     attendStatus = "qualified";
                  }
                  singleAttStatus.put(attendStatus);
                  studentAtt.put(finalAtt);
               }
               realAttendance.put(studentAtt); //[[1,2,3], [3,4,5]]
               allAttStatus.put(singleAttStatus);
            }
            JSONObject toSend = new JSONObject();
            toSend.put("names", names);
            toSend.put("ids", ids);
            toSend.put("attendance" ,realAttendance);
            toSend.put("attend_status",allAttStatus);
            worker.setResponseData(toSend);
            serv.messageToClient(worker);
        } catch (Exception ex) {
            Logger.getLogger(TestService.class.getName()).log(Level.SEVERE, null, ex);
            worker.setResponseData("fail");
            serv.messageToClient(worker);
        }
    }
    
    
    
    
    
    
    
}
