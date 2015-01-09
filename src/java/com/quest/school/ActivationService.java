/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.quest.school;

import com.quest.access.common.mysql.Database;
import com.quest.access.common.mysql.NonExistentDatabaseException;
import com.quest.access.control.Server;
import com.quest.access.useraccess.Serviceable;
import com.quest.access.useraccess.services.annotations.Endpoint;
import com.quest.access.useraccess.services.annotations.Model;
import com.quest.access.useraccess.services.annotations.Models;
import com.quest.access.useraccess.services.annotations.WebService;
import com.quest.access.useraccess.verification.UserAction;
import com.quest.servlets.ClientWorker;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 *
 * @author connie
 */

@WebService (name = "activation_service", level = 10, privileged = "yes")
@Models(models = {
    @Model(
       database = "school_data", table = "ACTIVATION_DATA", 
       columns = {"USER_EMAIL VARCHAR(100) PRIMARY KEY",
                  "ACTIVATION_KEY TEXT",
                  "CREATED DATETIME",
                  "SMS_ACCOUNT TEXT",
                  "VERSION_NO TEXT"}
        )
     }
)

public class ActivationService implements Serviceable {
    private static Database db;
    
    private static char [] secret = new char[]{'K','Z','M','1','3','A','J','H','N','O','C','Q','R','K','Z','N','U'};
    
    @Override
    public void service() {
      //dummy method 
    }

    @Override
    public void onCreate() {
         onStart();
    }
    
    @Endpoint(name="activation_details")
    public void activationDetails(Server serv, ClientWorker worker){
        JSONObject data = db.query("SELECT * FROM ACTIVATION_DATA");
        worker.setResponseData(data);
        serv.messageToClient(worker);
    
    }
    
    @Endpoint(name="validate_key")
    public void validateKey(Server serv, ClientWorker worker){
        try {
            JSONObject requestData = worker.getRequestData();
            String eAddress=requestData.optString("email_address");
            String aKey=requestData.optString("activation_key");
            String smsAcc=requestData.optString("sms_account");
            Object[] activeData = decodeKey(aKey, eAddress);
            UserAction action=new UserAction(serv, worker,"VALIDATE KEY : "+aKey);
            JSONObject data=new JSONObject();
            if(activeData==null){
               worker.setResponseData("fail");
               serv.messageToClient(worker);
            }
            else{          
                try {
                    db.execute("DELETE FROM ACTIVATION_DATA");
                    db.doInsert("ACTIVATION_DATA", new String[]{eAddress,aKey,"!NOW()",smsAcc,""});
                    JSONArray services=new JSONArray((ArrayList)activeData[1]);
                    data.put("expiry", activeData[0]+"00000");
                    data.put("services",services);
                    action.saveAction();
                    String vNo = serv.getConfig().getInitParameter("version-no");
                    String vName = serv.getConfig().getInitParameter("version-name");
                    data.put("version_no", vNo);
                    data.put("version_name", vName);
                    worker.setResponseData(data);
                    serv.messageToClient(worker);
                } catch (Exception ex) {
                    Logger.getLogger(ActivationService.class.getName()).log(Level.SEVERE, null, ex);
                }
              
            }
        } catch (Exception ex) {
            Logger.getLogger(ActivationService.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    
    
    
    private static ArrayList getSubscribedServices(String servicesHash){
	   try{
		 String[] test=new String[]{
				  "1000",
				  "1001",
				  "1010",
				  "1011",
				  "1100",
				  "1101",
				  "1110",
				  "1111",
				  
		  };
	   String [] dummy=new String[test.length];
	   ArrayList subscribedServices=new ArrayList();
	   for(int x=0; x<test.length; x++){
		 dummy[x]=test[x].replace("1", "true").replace("0", "false");
		 String genServicesHash=Security.toBase64(Security.makePasswordDigest(dummy[x], secret)).substring(0,7);
                 genServicesHash=genServicesHash.replace("/", "5");
		 genServicesHash=genServicesHash.replace("+", "3");
		 if(genServicesHash.equalsIgnoreCase(servicesHash)){
			if(test[x].charAt(1)=='1'){
			  subscribedServices.add("mark_service");	
			}
			if(test[x].charAt(2)=='1'){
			   subscribedServices.add("account_service");	
		    }
			if(test[x].charAt(3)=='1'){
			   subscribedServices.add("message_service");	
			}
		   subscribedServices.add("student_service");
		   return subscribedServices;
		 }
	   }
	  }
	  catch(Exception e){ 
	  }
	  return null;
	}
	
       private static Integer[] prodSum(String expiry){
	  Integer sum=0;
	  Integer prod=1;
	  for(int x=0; x<expiry.length(); x++){
		 Character ch=expiry.charAt(x);
		 Integer num=Integer.parseInt(ch.toString());
		 sum=sum+num;
		 if(num!=0){
		   prod=prod*num; 
		 }
	  }
	  return new Integer[]{sum,prod};
	}
        
	private static Object[] decodeKey(String key,String email){
	  try{
		String startExpiry=key.substring(14,16);
		String secondExpiry=key.substring(7,9);
		String thirdExpiry=key.substring(0,2);
		String fourthExpiry=key.substring(19,21);
		StringBuilder builder1=new StringBuilder();
		builder1.append(startExpiry).append(secondExpiry).append(thirdExpiry).append(fourthExpiry);
		String expiry=builder1.toString();
		Integer [] vals=prodSum(expiry);
		String prod=vals[1].toString();
		String sum=vals[0].toString();
		  if(prod.length()>2){
			  prod=prod.substring(0,2);
		  }
		  else if(prod.length()<2){
			  prod=prod+"0";  
		  }
		  
		  if(sum.length()>2){
			  sum=sum.substring(0,2);
		  }
		  else if(sum.length()<2){
			  sum=sum+"0";  
		  }
		
		String prodFromKey=key.substring(21,23);
		String sumFromKey=key.substring(23);
		String accountHash=Security.toBase64(Security.makePasswordDigest(email, secret)).substring(0,6);
                accountHash=accountHash.replace("/", "5");
		accountHash=accountHash.replace("+", "3").toUpperCase();
		String accountHashFromKey=key.substring(11,14)+key.substring(16,19);
		String servicesHash=key.substring(9,11)+key.substring(2,7);
		ArrayList subscribedServices=getSubscribedServices(servicesHash);
		if(prodFromKey.equals(prod) && sumFromKey.equals(sum) && accountHash.equals(accountHashFromKey) && subscribedServices!=null){
		   return new Object[]{expiry,subscribedServices};
		}
		return null;
	  }
	  catch(Exception e){
		  
	  }
	 return null;
	}
    
    @Override
    public void onStart() {
      //get an instance of our database
         try {
            //get an instance of our database
            db = Database.getExistingDatabase("school_data");
        } catch (NonExistentDatabaseException ex) {
            Logger.getLogger(MarkService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
