/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.quest.school;

import com.quest.access.common.UniqueRandom;
import com.quest.access.common.mysql.Database;
import com.quest.access.control.Server;
import com.quest.access.useraccess.Serviceable;
import com.quest.access.useraccess.services.Message;
import com.quest.access.useraccess.services.annotations.Endpoint;
import com.quest.access.useraccess.services.annotations.Model;
import com.quest.access.useraccess.services.annotations.Models;
import com.quest.access.useraccess.services.annotations.WebService;
import com.quest.access.useraccess.verification.UserAction;
import com.quest.servlets.ClientWorker;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 *
 * @author connie
 */
@WebService (name = "message_service", level = 10, privileged = "yes")
@Models(models = {
    @Model(
       database = "school_data", table = "TEMPLATE_MESSAGES",
       columns = {"ID VARCHAR(10) PRIMARY KEY", 
                  "TEMPLATE_NAME TEXT",
                  "TEMPLATE_VALUE TEXT",
                  "TEMPLATE_OWNER VARCHAR(25)",
                  "CREATED DATETIME"
       }),
     @Model(
       database = "school_data", table = "SMS_DATA",
       columns = {"ID VARCHAR(10) PRIMARY KEY", 
                  "SMS_COUNT INT",
                  "INITIATOR_ID VARCHAR(20)",
                  "ENTRY_TYPE BOOL", //0 means the sms are not charged, 1 means the sms have been charged
                  "CREATED DATETIME"
       }),
      @Model(
       database = "school_data", table = "SMS_LISTS",
       columns = {"ID VARCHAR(10) PRIMARY KEY", 
                   "LIST_ID VARCHAR(10)",
                   "PERSON_NAME TEXT",
                   "PERSON_PHONE VARCHAR(20)",
                   "CREATED DATETIME"
       }),
      @Model(
       database = "school_data", table = "SMS_LISTS_META_DATA",
       columns = {"ID VARCHAR(10) PRIMARY KEY", 
                   "LIST_NAME TEXT",
                   "CREATED DATETIME"
       })
   }
)
public class InternetMessageService implements Serviceable {
    private static Database db;
    private static final String PAY_URL = "https://quest-access.appspot.com/pay";
    private static ConcurrentHashMap<String,ArrayList<String>> failedRecipients=new ConcurrentHashMap();
    private static ConcurrentHashMap<String,ArrayList<String>> failedMessages=new ConcurrentHashMap();
    private static ConcurrentHashMap<String,ArrayList<String>> failedRecipientNames=new ConcurrentHashMap();
    private static ConcurrentHashMap<String, Integer> messageSent=new ConcurrentHashMap();
    private static ConcurrentHashMap<String,Boolean> stopSendingMessages = new ConcurrentHashMap();
    @Override
    public void service() {
      //dummy method
    }
    
    public static void main(String [] args){
       /*
        try {
            Database.setDefaultConnection("root","localhost","");
            db=Database.getExistingDatabase("school_data");
            JSONObject activeData=db.queryDatabase("SELECT SMS_ACCOUNT,USER_EMAIL FROM ACTIVATION_DATA");
            String phoneNo="0729936172";
            String msg="i am a cool person";
            String schoolName="Quest Shule Academy";
            String initiator="test";
            int  theCount=0;
            for(int x=0; x<100; x++){
               int count=mockCommitMessage(phoneNo,msg,schoolName,initiator, activeData);
               theCount=theCount+count;
            }
           System.out.println(theCount);
        } catch (NonExistentDatabaseException ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
        */
    }

    @Override
    public void onCreate() {
          onStart();
    }
    
    @Endpoint(name="create_message_list")
    public void createMessageList(Server serv,ClientWorker worker){
        try {
            JSONObject requestData = worker.getRequestData();
            JSONArray names = requestData.optJSONArray("names");
            JSONArray phones=requestData.optJSONArray("phones");
            String listName=requestData.optString("list_name");
            UserAction action=new UserAction(serv, worker, "CREATE MESSAGE LIST");
            boolean exists= db.ifValueExists(listName,"SMS_LISTS_META_DATA","LIST_NAME");
            if(exists){
               worker.setReason("List already exists");
               worker.setResponseData(Message.FAIL);
               serv.messageToClient(worker);
               return;
            }
            UniqueRandom rand=new UniqueRandom(10);
            String listId=rand.nextMixedRandom();
            db.doInsert("SMS_LISTS_META_DATA", new String[]{listId,listName,"!NOW()"});
            for(int x=0; x<names.length(); x++){
              db.doInsert("SMS_LISTS", new String[]{rand.nextMixedRandom(),listId,names.optString(x),phones.optString(x),"!NOW()"});
            }
            action.saveAction();
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Endpoint(name="all_message_lists")
    public void allMessageLists(Server serv,ClientWorker worker){
        JSONObject data = db.query("SELECT * FROM SMS_LISTS_META_DATA ORDER BY LIST_NAME ASC");
        worker.setResponseData(data);
        serv.messageToClient(worker);
    }
    
    @Endpoint(name="delete_message_list")
    public void deleteMessageList(Server serv,ClientWorker worker){
        try {
            JSONObject requestData = worker.getRequestData();
            String id = requestData.optString("id");
            UserAction action=new UserAction(serv, worker, "DELETE MESSAGE LIST");
            Database.executeQuery("DELETE FROM SMS_LISTS_META_DATA WHERE ID=?",db.getDatabaseName(),id);
            Database.executeQuery("DELETE FROM SMS_LISTS WHERE LIST_ID=?",db.getDatabaseName(),id);
            action.saveAction();
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Endpoint(name="all_list_members")
    public void allListMembers(Server serv,ClientWorker worker){
      JSONObject requestData = worker.getRequestData();
      String listId = requestData.optString("id"); 
      JSONObject data=db.query("SELECT * FROM SMS_LISTS WHERE LIST_ID=? ORDER BY PERSON_NAME ASC",listId);
      worker.setResponseData(data);
      serv.messageToClient(worker);
    }
    
    @Endpoint(name="edit_message_list")
    public void editMessageList(Server serv,ClientWorker worker){
        try {
            JSONObject requestData = worker.getRequestData();
            JSONArray names = requestData.optJSONArray("names");
            JSONArray phones=requestData.optJSONArray("phones");
            String listName=requestData.optString("list_name");
            String oldListName=requestData.optString("old_list_name");
            String listId=requestData.optString("id");
            UserAction action=new UserAction(serv, worker, "EDIT MESSAGE LIST");
            boolean exists= db.ifValueExists(listName,"SMS_LISTS_META_DATA","LIST_NAME");
            if(!listName.equals(oldListName) && exists){
               worker.setReason("List already exists");
               worker.setResponseData(Message.FAIL);
               serv.messageToClient(worker);
               return;
            }
            Database.executeQuery("DELETE FROM SMS_LISTS WHERE LIST_ID=?",db.getDatabaseName(),listId);
            UniqueRandom rand=new UniqueRandom(10);
            for(int x=0; x<names.length(); x++){
              db.doInsert("SMS_LISTS", new String[]{rand.nextMixedRandom(),listId,names.optString(x),phones.optString(x),"!NOW()"});
            }
            action.saveAction(); 
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    @Endpoint(name="all_recipients")
    public void allRecipients(Server serv,ClientWorker worker){
       JSONObject requestData = worker.getRequestData();
       String fieldType=requestData.optString("field_type");
       String fieldValue=requestData.optString("field_value");
       String classId=requestData.optString("class_id");
       String streamId=requestData.optString("stream_id");
       JSONObject data=new JSONObject();
       if(fieldType.equals("student")){
           String correctFieldName=fieldValue.replace(" ","_");
           if (classId.equals("all")) {
               data = db.query("SELECT " + correctFieldName + ", STUDENT_NAME FROM STUDENT_DATA ORDER BY STUDENT_NAME ASC");
           } else if (streamId.equals("all")) {
               data = db.query("SELECT " + correctFieldName + ", STUDENT_NAME FROM STUDENT_DATA WHERE STUDENT_CLASS=? ORDER BY STUDENT_NAME ASC", classId);
           } else {
               data = db.query("SELECT " + correctFieldName + ", STUDENT_NAME FROM STUDENT_DATA WHERE STUDENT_CLASS=? AND STUDENT_STREAM=? ORDER BY STUDENT_NAME ASC", classId, streamId);
           }  
       }
       else if(fieldType.equals("list")){
          data= db.query("SELECT PERSON_NAME,PERSON_PHONE FROM SMS_LISTS WHERE LIST_ID=? ORDER BY PERSON_NAME ASC",fieldValue);
       }
       worker.setResponseData(data);
       serv.messageToClient(worker);
    }
    
    @Endpoint(name="create_template")
    public void createTemplate(Server serv,ClientWorker worker){
        try {
            JSONObject requestData = worker.getRequestData();
            String tempName=requestData.optString("template_name");
            String tempValue=requestData.optString("template_value");
            boolean exists = db.ifValueExists(tempName, "TEMPLATE_MESSAGES", "TEMPLATE_NAME");
            if(exists){
               worker.setReason("The specified template already exists");
               worker.setResponseData(Message.FAIL);
               serv.messageToClient(worker);
               return;
            }
            String userId=(String) worker.getSession().getAttribute("userid");
            UniqueRandom rand=new UniqueRandom(10);
            db.doInsert("TEMPLATE_MESSAGES", new String[]{rand.nextMixedRandom(),tempName,tempValue,userId,"!NOW()"});
            UserAction action=new UserAction(serv, worker, "MESSAGE_SERVICE : CREATE TEMPLATE :  "+tempName);
            action.saveAction();
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Endpoint(name="fetch_template_names")
    public void fetchTemplateNames(Server serv,ClientWorker worker){
       String userId=(String) worker.getSession().getAttribute("userid");
       JSONObject data = db.query("SELECT ID,TEMPLATE_NAME FROM TEMPLATE_MESSAGES WHERE TEMPLATE_OWNER=?",userId);
       worker.setResponseData(data);
       serv.messageToClient(worker);
    }
   
    @Endpoint(name="fetch_template")
    public void fetchTemplate(Server serv,ClientWorker worker){
       JSONObject requestData = worker.getRequestData();
       String id = requestData.optString("template_id");
       JSONObject data = db.query("SELECT ID,TEMPLATE_VALUE,TEMPLATE_NAME FROM TEMPLATE_MESSAGES WHERE ID=?",id);
       worker.setResponseData(data);
       serv.messageToClient(worker);
    }
    
      @Endpoint(name="delete_template")
    public void deleteTemplate(Server serv,ClientWorker worker){
        try {
            JSONObject requestData = worker.getRequestData();
            String id = requestData.optString("template_id");
            UserAction action=new UserAction(serv, worker, "MESSAGE_SERVICE : DELETE TEMPLATE "+id);
            Database.executeQuery("DELETE FROM TEMPLATE_MESSAGES WHERE ID=?",db.getDatabaseName(),id);
            action.saveAction();
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Endpoint(name="update_template")
    public void updateTemplate(Server serv,ClientWorker worker){
        try {
            JSONObject requestData = worker.getRequestData();
            String id = requestData.optString("template_id");
            String name = requestData.optString("template_name");
            String value=requestData.optString("template_value");
            db.execute("UPDATE TEMPLATE_MESSAGES SET TEMPLATE_NAME='"+name+"', TEMPLATE_VALUE='"+value+"' WHERE ID='"+id+"' ");
            UserAction action=new UserAction(serv, worker, "MESSAGE_SERVICE : UPDATE TEMPLATE :  "+name);
            action.saveAction();
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public void onStart() {
            try {
                //get an instance of our database
                db=Database.getExistingDatabase("school_data");
          	
            } catch (Exception ex) {
                Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
            }
    }
    
   @Endpoint(name="recipient_fields")
   public void allRecipientFields(Server serv,ClientWorker worker){
        try {
            
            JSONObject studentFields=db.query("SELECT FIELD_NAME FROM STUDENT_FIELD_META_DATA");
            JSONObject all=new JSONObject();
            all.put("student_fields", studentFields);
            worker.setResponseData(all);
            serv.messageToClient(worker);
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
   }
   
   
  @Endpoint(name="target_reach")
  public void getTargetReach(Server serv,ClientWorker worker){
        try {
            JSONObject requestData = worker.getRequestData();
            JSONArray fields = requestData.optJSONArray("student_fields");
            JSONArray listIds = requestData.optJSONArray("list_ids");
            String classId=requestData.optString("class_id");
            String streamId=requestData.optString("stream_id");
            String correctFieldName=fields.optString(0).replace(" ","_");
            JSONObject count=new JSONObject();
            Integer finalCount;
            if(!correctFieldName.isEmpty()){//count student fields
                if(classId.equals("all")){
                    count = db.query("SELECT COUNT("+correctFieldName+") AS TARGET_REACH FROM STUDENT_DATA");
                }
                else if(streamId.equals("all")){
                    count = db.query("SELECT COUNT("+correctFieldName+") AS TARGET_REACH FROM STUDENT_DATA WHERE STUDENT_CLASS=?",classId);
                }
                else {
                    count = db.query("SELECT COUNT("+correctFieldName+") AS TARGET_REACH FROM STUDENT_DATA WHERE STUDENT_CLASS=? AND STUDENT_STREAM=?",classId,streamId);
                }
                JSONArray dbCount = count.optJSONArray("TARGET_REACH");
                Integer realCount=dbCount.optInt(0);
                finalCount=fields.length()*realCount;
            }
            else{
              finalCount=0;  
            }
            //add the count due to the lists
            for(int x=0; x<listIds.length(); x++){
                int listCount= db.query("SELECT COUNT(LIST_ID) AS TARGET_REACH "
                      + "FROM SMS_LISTS WHERE LIST_ID=?",listIds.optString(x)).optJSONArray("TARGET_REACH").optInt(0);
                finalCount=finalCount+listCount;
            }
            count.put("TARGET_REACH", finalCount);
            worker.setResponseData(count);
            serv.messageToClient(worker);
        } catch (JSONException ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
  }
  
  @Endpoint(name="preview_message")
  public void previewMessage(Server serv,ClientWorker worker){
        try {
            JSONObject requestData = worker.getRequestData();
            String msg=requestData.optString("msg");  
            String classId=requestData.optString("class_id");
            String streamId=requestData.optString("stream_id");
            Object[] tempData=parseMessage(msg, serv, worker);
            String [] messageTemplate=(String[]) tempData[0];
            HashMap locations=(HashMap<Integer,JSONObject>) tempData[1];
            Iterator iter=locations.keySet().iterator();
            JSONObject studentData;
            if(classId.equals("all")){
                studentData = db.query("SELECT * FROM STUDENT_DATA");
            }
            else if(streamId.equals("all")){
                studentData = db.query("SELECT * FROM STUDENT_DATA WHERE STUDENT_CLASS=?",classId);
            }
            else {
                studentData = db.query("SELECT * FROM STUDENT_DATA WHERE STUDENT_CLASS=? AND STUDENT_STREAM=?",classId,streamId);
            }
            while (iter.hasNext()) {
                Integer count = (Integer) iter.next();
                JSONObject template = (JSONObject) locations.get(count);
                String temp = template.optString("template"); //the message needs to specify a template for further processing
                JSONObject data = template.optJSONObject("data"); //this is the data to be sent
                String table = data.optString("table"); //specify a table to get data from
                String column = data.optString("column"); //specify a column to get data from
                if (!temp.isEmpty()) {
                    String value = resolveTemplates(table, temp, studentData, 0);
                    messageTemplate[count] = value;
                } else {
                    JSONArray col = studentData.optJSONArray(column.toUpperCase());
                    String value = col.optString(0);
                    messageTemplate[count] = value;
                }
            }
            String finalMessage="";
            for(int z=0; z<messageTemplate.length; z++){
               finalMessage=finalMessage+messageTemplate[z];
            }
            JSONObject schoolSmsData=db.query("SELECT USER_EMAIL FROM ACTIVATION_DATA");
            String schoolName = schoolSmsData.optJSONArray("USER_EMAIL").optString(0);
            finalMessage=finalMessage+" From : "+schoolName;
            int count=((int)Math.ceil(finalMessage.length()/160.0) );
            JSONObject obj=new JSONObject();
            obj.put("message_preview",finalMessage);
            obj.put("count", count);
            worker.setResponseData(obj);
            serv.messageToClient(worker);
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
  }
  
  @Endpoint(name="send_message")
  public synchronized void sendMessage(Server serv,ClientWorker worker){
    JSONObject requestData = worker.getRequestData();
    String msg=requestData.optString("msg");
    String id=worker.getSession().getId();
    stopSendingMessages.put(id,false);
    messageSent.put(id,0);
    failedMessages.put(id, new ArrayList<String>());
    failedRecipients.put(id, new ArrayList<String>());
    failedRecipientNames.put(id, new ArrayList<String>());
    Object[] tempData=parseMessage(msg, serv, worker);
    if(tempData==null){
       //we cannot send the message 
    }
    else{
      sendFinalMessage(serv,worker,tempData,msg);
    }
    
  }
  
  private void sendFinalMessage(Server serv,ClientWorker worker,Object [] tempData,String msg){
        try {
            JSONObject schoolSmsData=db.query("SELECT SMS_ACCOUNT,USER_EMAIL FROM ACTIVATION_DATA");
            String id= worker.getSession().getId();
            String smsAccount=schoolSmsData.optJSONArray("SMS_ACCOUNT").optString(0);
            String schoolName = schoolSmsData.optJSONArray("USER_EMAIL").optString(0);
            JSONObject activeData=db.query("SELECT SMS_ACCOUNT,USER_EMAIL FROM ACTIVATION_DATA");
            boolean isValid=validateSMSAccount(smsAccount);
            JSONObject balData=fetchSMSBalance(smsAccount); //data about sms balance
            
            int balance=balData.optInt("sms_balance");
            String accType=balData.optString("sms_account_type");
            if(!isValid){
               //this school has no valid sms account
                worker.setResponseData(Message.FAIL);
                serv.messageToClient(worker);
                return;
            }
            
            int sentCount=0; //how many messages were actually sent
            String initiator = (String) worker.getSession().getAttribute("username"); //who is sending messages
            JSONObject requestData = worker.getRequestData();
            JSONArray fields = requestData.optJSONArray("student_fields");
            JSONArray listIds=requestData.optJSONArray("list_ids");
            JSONArray phoneNos=requestData.optJSONArray("phones"); //edited phone nos
            JSONArray names=requestData.optJSONArray("names"); //edited names
            for(int x=0; x<listIds.length(); x++){
               JSONObject listData=db.query("SELECT PERSON_PHONE,PERSON_NAME FROM SMS_LISTS WHERE LIST_ID=?",listIds.optString(x));
               JSONArray listPhones =listData.optJSONArray("PERSON_PHONE");
               JSONArray listNames =listData.optJSONArray("PERSON_NAME");
               //get the phone numbers from lists and append to the phone numbers from edits
               phoneNos.toList().addAll(listPhones.toList());
               names.toList().addAll(listNames.toList());
            }
            for(int y=0; y<phoneNos.length(); y++){
               if(sentCount>=balance && accType.equals("prepaid")){
                   //we should stop you from sending messages if sms balance is 0 and sms account is prepaid
                   //we should allow you to send if your account is postpaid
                   //charge account and exit
                   System.out.println("insufficient balance in sms_account");
                   noteSMSUsage(sentCount,worker,activeData,initiator);
                   worker.setResponseData(Message.FAIL);
                   serv.messageToClient(worker);
                   return;
                }
               else if(stopSendingMessages.get(id)){
                 noteSMSUsage(sentCount,worker,activeData,initiator);
                 worker.setResponseData(Message.SUCCESS);
                 serv.messageToClient(worker);
                 return;
              }
              String phoneNo=phoneNos.optString(y);
              String name=names.optString(y);
              int count=commitMessage(phoneNo,msg,schoolName,id,initiator,worker,activeData,name);
              sentCount=sentCount+count;
              //send the list messages
            }
            
            String classId=requestData.optString("class_id");
            String streamId=requestData.optString("stream_id");
            String [] messageTemplate=(String[]) tempData[0];
            HashMap locations=(HashMap<Integer,JSONObject>) tempData[1];
            JSONObject studentData;
            if(classId.equals("all")){
               studentData = db.query("SELECT * FROM STUDENT_DATA");
            }
            else if(streamId.equals("all")){
               studentData = db.query("SELECT * FROM STUDENT_DATA WHERE STUDENT_CLASS=?",classId);
            }
            else {
               studentData = db.query("SELECT * FROM STUDENT_DATA WHERE STUDENT_CLASS=? AND STUDENT_STREAM=?",classId,streamId);
            }
            int length=studentData.optJSONArray("STUDENT_NAME").length();
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
            UserAction action=new UserAction(serv, worker, "MESSAGE_SERVICE : SEND MESSAGE TO CLASS: "+classId+" STREAM: "+streamId);
            for(int x=0; x<length; x++){
                Iterator iter=locations.keySet().iterator();
                if(stopSendingMessages.get(id)){
                   noteSMSUsage(sentCount,worker,activeData,initiator);
                   return; 
                }
                while(iter.hasNext()){
                     Integer count=(Integer) iter.next();
                     JSONObject template=(JSONObject) locations.get(count);
                     String temp = template.optString("template"); //the message needs to specify a template for further processing
                     JSONObject data = template.optJSONObject("data"); //this is the data to be sent
                     String table = data.optString("table"); //specify a table to get data from
                     String column=data.optString("column"); //specify a column to get data from
                     if(!temp.isEmpty()){
                         String value=resolveTemplates(table,temp,studentData,x);
                         messageTemplate[count]=value;
                     }
                     else{
                         JSONArray col = studentData.optJSONArray(column.toUpperCase());
                         String value = col.optString(x);
                         messageTemplate[count]=value;
                     }
                }
             
                if(sentCount>=balance && accType.equals("prepaid")){
                   //we should stop you from sending messages if sms balance is 0 and sms account is prepaid
                   //we should allow you to send if your account is postpaid
                   //charge account and exit
                   noteSMSUsage(sentCount,worker,activeData,initiator);
                   System.out.println("insufficient balance in sms_account");
                   return;
                }
                String studentName=studentData.optJSONArray("STUDENT_NAME").optString(x);
                int singleSentCount = disperseSchoolMessage(messageTemplate, studentData, fields, x,schoolName,id,initiator,worker,activeData,studentName);
                sentCount=sentCount+singleSentCount;
            }
           System.out.println("local_message_sent_count: "+sentCount);
           noteSMSUsage(sentCount,worker,activeData,initiator);
           fetchSMSBalance(smsAccount);
           action.saveAction();
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
      
  }
  
  private void messageWasSent(String id){
    Integer val = messageSent.get(id);
    val++;
    messageSent.put(id, val);
  }
  
    private void messageWasNotSent(String id,String msg,String rcpPhone,String rcpName){
        failedMessages.get(id).add(msg);
        failedRecipients.get(id).add(rcpPhone);
        failedRecipientNames.get(id).add(rcpName);
    }
  
  @Endpoint(name="resend_failed_messages")
  public synchronized void resendFailedMessages(Server serv, ClientWorker worker){
        try {
            String initiator=(String) worker.getSession().getAttribute("username");
            JSONObject schoolSmsData=db.query("SELECT SMS_ACCOUNT,USER_EMAIL FROM ACTIVATION_DATA");
            String smsAccount=schoolSmsData.optJSONArray("SMS_ACCOUNT").optString(0);
            UserAction action=new UserAction(serv, worker, "RESEND FAILED MESSAGES");
             JSONObject activeData=db.query("SELECT SMS_ACCOUNT,USER_EMAIL FROM ACTIVATION_DATA");
            boolean isValid=validateSMSAccount(smsAccount);
            if(!isValid){
               worker.setReason("No sms account specified");
               worker.setResponseData(Message.FAIL);
               serv.messageToClient(worker);
               return;
            }
            int sentMessageCount=0;
            JSONObject balData=fetchSMSBalance(smsAccount);
            int balance=balData.optInt("sms_balance");
            String accType=balData.optString("sms_account_type");
            String id=worker.getSession().getId();
            ArrayList<String> failedRcp=failedRecipients.get(id);
            if(failedRcp==null){
               return;
            }
            ArrayList<String> failedName=failedRecipientNames.get(id);
            ArrayList<String> failedMsg=failedMessages.get(id);
            ArrayList<String> newFailedRcp=new ArrayList();
            ArrayList<String> newFailedMsg=new ArrayList();
            ArrayList<String> newFailedName=new ArrayList();
            newFailedRcp.addAll(failedRcp);
            newFailedMsg.addAll(failedMsg);
            newFailedName.addAll(failedName);
            failedRecipients.put(id,new ArrayList<String>());
            failedMessages.put(id, new ArrayList<String>());
            failedRecipientNames.put(id, new ArrayList<String>());
            messageSent.put(id,0);
            for(int x=0; x<failedRcp.size(); x++){
               if(stopSendingMessages.get(id)){ 
                  noteSMSUsage(sentMessageCount,worker,activeData,initiator);
                  return; 
               }
               if(sentMessageCount>=balance && accType.equals("prepaid")){
                  //you have sent enough messages charge and exit
                   System.out.println("insufficient balance in sms_account");
                   noteSMSUsage(sentMessageCount,worker,activeData,initiator);
                   return;
                }
               String rcp=newFailedRcp.get(x);
               String msg=newFailedMsg.get(x);
               String name=newFailedName.get(x);
               int sentCount=commitMessage(rcp, msg,"", id, initiator, worker,activeData,name);
               sentMessageCount=sentMessageCount+sentCount;
            }
            worker.setResponseData(Message.SUCCESS);
            serv.messageToClient(worker);
            action.saveAction();
            failedRecipients.put(id,new ArrayList<String>());
            failedMessages.put(id,new ArrayList<String>());
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
  }
  
  private boolean validateSMSAccount(String account){
      try {
            JSONObject request=new JSONObject();
            request.put("message", "validate_account");
            request.put("sms_account", account);
            JSONObject response = sendRemoteData(request, PAY_URL); 
            if(response!=null){
               if(response.optString("response").equals("success")){
                  //account is valid
                   System.out.println("sms account is valid");
                   return true;
               }
               System.out.println("sms account is invalid");
               return false;
            }
            else {
               //we cant tell that the account is valid
                System.out.println("sms account is invalid");
                return false;
            }
        } catch (JSONException ex) {
            System.out.println("sms account is invalid");
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }   
  }
  
  @Endpoint(name="sms_data")
  public void smsData(Server serv, ClientWorker worker){
      String smsAccount=db.query("SELECT SMS_ACCOUNT FROM ACTIVATION_DATA").optJSONArray("SMS_ACCOUNT").optString(0);
      JSONObject data = fetchSMSBalance(smsAccount);
      worker.setResponseData(data);
      serv.messageToClient(worker);
  }
  
  private JSONObject fetchSMSBalance(String account){
      try {
            JSONObject request=new JSONObject();
            request.put("message", "sms_balance");
            request.put("sms_account", account);
            JSONObject response = sendRemoteData(request, PAY_URL);
            System.out.println("sms account data : "+response.toString());
            return response;
          
        } catch (JSONException ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }   
  }
     //we need to deduct the amount of messages this school has
     //as per their usage, the fast step is to get their sms account
     //no and then use it to charge it, since we are using google cloud nosql
     //we can only charge one school connection at a time
     //we need to charge in bulk i.e deduct after all messages have been sent
     //if we have sent a number of messages and the system fails, store this
     //number of messages locally and the first thing we do when somebody tries
     //to send a new message is to charge their account
     //so we will have a table called SMS_DATA with pending charges and the initiators name
     //when we start we charge all the pending charges and then try to send again
  /*
   * @param smsCount this is the number of sms we need to deduct from this school's sms account
   * @param initiator this is the person sending the messages
   */
  private static void chargeAccount(Integer smsCount, String initiator,JSONObject activeData){
        try {
            //we need the sms account
            String smsAccount=  activeData.optJSONArray("SMS_ACCOUNT").optString(0);
            String sName=  activeData.optJSONArray("USER_EMAIL").optString(0);
            //ID,SMS_COUNT,USER_NAME
            //now charge this account, if we fail to charge and we have sent messages
            //store this in SMS_DATA
            JSONObject toCharge=new JSONObject();
            toCharge.put("ID", new JSONArray().put(""));
            toCharge.put("SMS_COUNT", smsCount);
            toCharge.put("USER_NAME", initiator);
            toCharge.put("SMS_ACCOUNT", smsAccount);
            toCharge.put("message","charge_account");
            toCharge.put("school_name", sName);
            JSONObject response=sendRemoteData(toCharge, PAY_URL); //make any pending charges
            if(response!=null){
               if(response.optString("response").equals("success")){
                 //account charged successfully
                  System.out.println("account charged successfully for current");
               }
            }
            else {
               //this means charging failed so add the records to the pending data database
                System.out.println("account charging failed for current");
              
            }
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
  }
  
   private static void noteSMSUsage(Integer smsCount,ClientWorker worker,JSONObject activeData,String initiator){
        try {
            UniqueRandom rand=new UniqueRandom(10);
            String userId=(String) worker.getSession().getAttribute("userid");
            //remote note
            String smsAccount=  activeData.optJSONArray("SMS_ACCOUNT").optString(0);
            String sName=  activeData.optJSONArray("USER_EMAIL").optString(0);
                //ID,SMS_COUNT,USER_NAME
                //now charge this account, if we fail to charge and we have sent messages
                //store this in SMS_DATA
           JSONObject usage = new JSONObject();
           usage.put("ID", new JSONArray().put(""));
           usage.put("SMS_COUNT", smsCount);
           usage.put("USER_NAME", initiator);
           usage.put("SMS_ACCOUNT", smsAccount);
           usage.put("message", "sms_usage");
           usage.put("school_name", sName);
           JSONObject response = sendRemoteData(usage, PAY_URL); //make any pending charges
           if (response != null) {
               if (response.optString("response").equals("success")) {
                   db.doInsert("SMS_DATA", new String[]{rand.nextMixedRandom(),smsCount.toString(),userId,"1", "!NOW()"}); //local note
                   System.out.println("sms usage noted successfully");
                   
               }
           } else {
               db.doInsert("SMS_DATA", new String[]{rand.nextMixedRandom(),smsCount.toString(),userId,"1", "!NOW()"}); //local note
               System.out.println("sms usage noting failed");

           }
        } catch (JSONException ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
  
  
    private static int mockCommitMessage(String phoneNo,String msg,String schoolName,String initiator,JSONObject activeData){
        int sentCount=0;
        phoneNo = correctPhoneNo(phoneNo);
        msg=msg+" From : "+schoolName;
        System.out.println("request_send: "+phoneNo+" --> "+msg);
        try {
           // JSONArray result = messageService.sendMessage(phoneNo, msg);
           // JSONObject response = result.getJSONObject(0);
            String status="success";
            if(status.equalsIgnoreCase("success")){
               //if it was not sent, queue it again for sending 
               System.out.println("message_sent: "+phoneNo+" --> "+msg);
               sentCount=sentCount+((int)Math.ceil(msg.length()/160.0) ); //calculate the number of messages sent, one message is 160 characters
               mockChargeAccount(sentCount, initiator,activeData); //we charge at the end of sending
            }
            else{
                
            }
            return sentCount; 
        } catch (Exception ex) {
             Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
             return sentCount; 
        } 
     }
  
  private static void mockChargeAccount(Integer smsCount, String initator,JSONObject activeData){
   try {
            //we need the sms account
           
            String smsAccount=  activeData.optJSONArray("SMS_ACCOUNT").optString(0);
            String sName=  activeData.optJSONArray("USER_EMAIL").optString(0);
            UniqueRandom rand=new UniqueRandom(10);
            String userId="123456789";
            //ID,SMS_COUNT,USER_NAME
            //now charge this account, if we fail to charge and we have sent messages
            //store this in SMS_DATA
            JSONObject toCharge=new JSONObject();
            toCharge.put("ID", new JSONArray().put(""));
            toCharge.put("SMS_COUNT", smsCount);
            toCharge.put("USER_NAME", initator);
            toCharge.put("SMS_ACCOUNT", smsAccount);
            toCharge.put("message","charge_account");
            toCharge.put("school_name", sName);
            JSONObject response=sendRemoteData(toCharge, PAY_URL); //make any pending charges
            if(response!=null){
               if(response.optString("response").equals("success")){
                 //account charged successfully
                  System.out.println("account charged successfully for current");
                  db.doInsert("SMS_DATA", new String[]{rand.nextMixedRandom(),smsCount.toString(),userId,"1", "!NOW()"});
               }
            }
            else {
               //this means charging failed so add the records to the pending data database
                System.out.println("account charging failed for current");
                db.doInsert("SMS_DATA", new String[]{rand.nextMixedRandom(),smsCount.toString(),userId,"0", "!NOW()"});
            }
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
  }
  
  
 
  
  
  public static JSONObject sendRemoteData(JSONObject data,String remoteUrl){
        try {
            String urlParams = URLEncoder.encode("json", "UTF-8") + "=" + URLEncoder.encode(data.toString(), "UTF-8");
            URL url = new URL(remoteUrl);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("Accept", "application/json");
            HttpURLConnection httpConn = (HttpURLConnection) conn;
            httpConn.setRequestMethod("POST");
            httpConn.setDoOutput(true);
	    DataOutputStream wr = new DataOutputStream(httpConn.getOutputStream());
	    wr.writeBytes(urlParams);
            wr.flush();
	    wr.close();
            int responseCode = httpConn.getResponseCode();
            BufferedReader reader;
            if(responseCode == 200) {
                reader = new BufferedReader(new InputStreamReader(httpConn.getInputStream()));
                String inputLine = reader.readLine();
                reader.close();
                return new JSONObject(inputLine);
            } else {
               return null;
            }
        } catch (Exception ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
  }
  
  @Endpoint(name="stop_sending_messages")
  public void stopMessagePropagation(Server serv, ClientWorker worker){
       String id = worker.getSession().getId();
       stopSendingMessages.put(id,true);
       worker.setResponseData(Message.SUCCESS);
       serv.messageToClient(worker);
  }
  
  
  @Endpoint(name="check_message_progress")
  public void checkMessageProgress(Server serv, ClientWorker worker){
        try {
            serv.setDebugMode(true);
            String id=worker.getSession().getId();
            JSONObject data=new JSONObject();
            Integer val = messageSent.get(id);
            ArrayList<String> failed = failedMessages.get(id);
            int size;
            if(val==null){
             val=0;
            }
            if(failed==null){
               size=0; 
            }
            else{
              size=failed.size();
            }
            JSONArray rcpNames=new JSONArray(failedRecipientNames.get(id));
            JSONArray rcpPhones=new JSONArray(failedRecipients.get(id));
            data.put("message_sent", val);
            data.put("message_fail", size);
            data.put("failed_rcp_names", rcpNames);
            data.put("failed_rcp_phones", rcpPhones);
            worker.setResponseData(data);
            serv.messageToClient(worker);
        } catch (JSONException ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
        }
  }
  
  
  private static String correctPhoneNo(String phoneNo){
     if(phoneNo.startsWith("0")){
        phoneNo="+254"+phoneNo.substring(1);
     }
     else if(phoneNo.startsWith("254")){
        phoneNo="+"+phoneNo;
     }
     else{
        phoneNo="+254"+phoneNo; 
     }
     return phoneNo;
  }
  
  private int commitMessage(String phoneNo,String msg,String schoolName,String id,String initiator,ClientWorker worker,JSONObject activeData,String rcpName){
        int sentCount=0;
        phoneNo = correctPhoneNo(phoneNo);
        msg=msg+" From : "+schoolName;
        System.out.println("request_send: "+phoneNo+" --> "+msg);
         try {
            //remote note
            String smsAccount=  activeData.optJSONArray("SMS_ACCOUNT").optString(0);
            String sName=  activeData.optJSONArray("USER_EMAIL").optString(0);
                //ID,SMS_COUNT,USER_NAME
                //now charge this account, if we fail to charge and we have sent messages
                //store this in SMS_DATA
           JSONObject send = new JSONObject();
           send.put("USER_NAME", initiator);
           send.put("SMS_ACCOUNT", smsAccount);
           send.put("PHONE_NO",phoneNo);
           send.put("MESSAGE",msg);
           send.put("school_name", sName);
           send.put("message", "send_message");
           JSONObject response = sendRemoteData(send, PAY_URL); //make any pending charges
           if (response != null) {
               if (response.optString("response").equals("success")) {
                  System.out.println("message_sent: "+phoneNo+" --> "+msg);
                  sentCount = ((int)Math.ceil(msg.length()/160.0) ); //calculate the number of messages sent, one message is 160 characters
                  messageWasSent(id); 
               }
               else{
                  messageWasNotSent(id,msg,phoneNo,rcpName);  
               }
           } else {
               messageWasNotSent(id,msg,phoneNo,rcpName);
           }
           return sentCount;
        } catch (JSONException ex) {
            Logger.getLogger(InternetMessageService.class.getName()).log(Level.SEVERE, null, ex);
            return sentCount;
        }
     }
  
 
  
  
  private int disperseSchoolMessage(String [] messageTemplate,JSONObject data,JSONArray fields,int count,String schoolName,String id,String initiator,ClientWorker worker,JSONObject activeData,String rcpName){
     String msg="";
     int sentCount=0;
     for(int z=0; z<messageTemplate.length; z++){
         msg=msg+messageTemplate[z];
     }
     for(int y=0; y<fields.length(); y++){
        String field=fields.optString(y).replace(" ", "_");
        String phoneNo = data.optJSONArray(field.toUpperCase()).optString(count);
        int singleCount=commitMessage(phoneNo, msg, schoolName,id,initiator,worker,activeData,rcpName);
        sentCount=sentCount+singleCount;
     }
     return sentCount;
  }
  
  private Object[] parseMessage(String msg,Server serv,ClientWorker worker){
       // we are going to send custom messages to each phone number
      StringTokenizer token=new StringTokenizer(msg, "$"); 
      String [] msgTemplate =new String[token.countTokens()];
      HashMap locations=new HashMap();
      int count=0;
      while(token.hasMoreTokens()){
        String tk=token.nextToken();
        if(tk.startsWith("{")){
           try {
               //this is a json object template, so parse it
               JSONObject template =new JSONObject(tk);
               //check the data key
               JSONObject msgData= template.optJSONObject("data");
               if(msgData==null ){
                    //this information is insufficient we need a data key
                 worker.setReason("The data key is missing");
                 worker.setResponseData(Message.FAIL);
                 serv.messageToClient(worker);
                 return null;
               }
               else{
                  String table = msgData.optString("table");
                  String col = msgData.optString("column");
                  if(table.isEmpty() && col.isEmpty()){
                     worker.setReason("No table or column specified for data key");
                     worker.setResponseData(Message.FAIL);
                     serv.messageToClient(worker);
                     return null;  
                  }
                  else{
                    locations.put(count,template);
                  }
               }
           } catch (Exception ex) {
               //this means this is an invalid json string,tell the client that.
               worker.setResponseData( new Exception("The template "+tk+" is invalid"));
               serv.exceptionToClient(worker);
               return null;
           }
       }
       else{ 
         msgTemplate[count]=tk;
       }
      count++;
     }
   return new Object [] {msgTemplate,locations};
}
  private String resolveTemplates(String table,String template,JSONObject data,int count){
      String examId = db.query("SELECT ID FROM EXAM_DATA WHERE EXAM_NAME = ?",table).optJSONArray("ID").optString(0);
      if(template.equals("exam")){
         return examSimpleTemplate(examId,data,count);
      }
      else if(template.equals("exam2")){
        return examAdvancedTemplate(examId,data,count);
      }
      return "";
  }
  
  private String examAdvancedTemplate(String examId,JSONObject data,int count){
     try{
      String studentId=data.optJSONArray("ID").optString(count);
      String classId=data.optJSONArray("STUDENT_CLASS").optString(count);
      String streamId=data.optJSONArray("STUDENT_STREAM").optString(count);
      JSONObject reportForm = MarkService.openAdvancedReportForm(studentId, examId,streamId,classId);
      StringBuilder builder=new StringBuilder();
      JSONArray subjects=reportForm.optJSONObject("subject_data").optJSONArray("SUBJECT_NAME"); 
      for(int x=0; x<subjects.length(); x++){
         String subjectName=subjects.optString(x);
         JSONArray subIds=reportForm.optJSONArray("student_marks").optJSONObject(0).optJSONArray("SUBJECT_ID");
         String currentSubjectId=reportForm.optJSONObject("subject_data").optJSONArray("ID").optString(x);
         int index= subIds.toList().indexOf(currentSubjectId);
         String markValue=reportForm.optJSONArray("average_marks").optString(index);
         if(markValue.equals("")){
            markValue="0"; 
         }
         builder.append(subjectName).append(":").append(Math.round(Double.parseDouble(markValue))).append(", ");
      }
      builder.append("Grand Total : ").append(reportForm.optInt("grand_total")).append("/").append(reportForm.optInt("max_score")).append(", ");
      builder.append("Stream : ").append(reportForm.optInt("stream_rank")).append("/").append(reportForm.optInt("total_in_stream")).append(", ");
      builder.append("Class : ").append(reportForm.optInt("class_rank")).append("/").append(reportForm.optInt("total_in_class")).append(" ");
      return builder.toString();
     }
     catch(Exception e){
        return "";
     }
   }
 
  
  private  String examSimpleTemplate(String examId,JSONObject data,int count){
     try{
      String studentId=data.optJSONArray("ID").optString(count);
      String classId=data.optJSONArray("STUDENT_CLASS").optString(count);
      String streamId=data.optJSONArray("STUDENT_STREAM").optString(count);
      JSONObject reportForm = MarkService.openSingleReportForm(studentId, examId,streamId,classId);
      StringBuilder builder=new StringBuilder();
      JSONArray subjects=reportForm.optJSONObject("subject_data").optJSONArray("SUBJECT_NAME"); 
      for(int x=0; x<subjects.length(); x++){
         String subjectName=subjects.optString(x);
         String currentSubjectId=reportForm.optJSONObject("subject_data").optJSONArray("ID").optString(x);
         int index= reportForm.optJSONObject("student_marks").optJSONArray("SUBJECT_ID").toList().indexOf(currentSubjectId);
         String markValue=reportForm.optJSONObject("student_marks").optJSONArray("MARK_VALUE").optString(index);
         if(markValue.equals("")){
            markValue="0"; 
         }
         builder.append(subjectName).append(":").append(Math.round(Double.parseDouble(markValue))).append(", ");
      }
      builder.append("Grand Total : ").append(reportForm.optInt("grand_total")).append("/").append(reportForm.optInt("max_score")).append(", ");
      builder.append("Stream : ").append(reportForm.optInt("stream_rank")).append("/").append(reportForm.optInt("total_in_stream")).append(", ");
      builder.append("Class : ").append(reportForm.optInt("class_rank")).append("/").append(reportForm.optInt("total_in_class")).append(" ");
     return builder.toString();
     }
     catch(Exception e){
         return "";
     }
   }
 
}
