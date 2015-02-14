/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.quest.servlets;

import com.quest.access.common.Launcher;
import com.quest.access.common.io;
import com.quest.access.common.mysql.Database;
import com.quest.access.control.Server;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.AsyncContext; 
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet; 
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.json.JSONException;
import org.json.JSONObject;


/** 
 *
 * @author connie
 */

@WebServlet(name = "ServerLink", urlPatterns = {"/ServerLink"},asyncSupported=true)
public class ServerLink extends HttpServlet {
    private static Server server;
  
    private static  ArrayList skippablesMessages=new ArrayList();
    private static  ArrayList skippablesServices=new ArrayList();
    private static boolean applicationIsValid=false;
    private static ArrayList servicesSubscribedFor = new ArrayList();
    private static char[] secret=new char[]{'K','Z','M','1','3','A','J','H','N','O','C','Q','R','K','Z','N','U'};
    static{
       skippablesMessages.add("login");
       skippablesMessages.add("changepass");
       skippablesMessages.add("validate_key");
       skippablesMessages.add("logout");
       skippablesMessages.add("activation_details");
       
       skippablesServices.add("");
       skippablesServices.add("");
       skippablesServices.add("activation_service");
       skippablesServices.add("");
       skippablesServices.add("activation_service");
    }

    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try { 
             response.setContentType("text/html;charset=UTF-8");
             String json = request.getParameter("json");
             if(json == null)
                return;
             HttpSession session = request.getSession();
            //session, request, response
             JSONObject obj = new JSONObject(json);
             JSONObject headers = obj.optJSONObject("request_header");
             String msg = headers.optString("request_msg");
             String sessionId = headers.optString("session_id");
             boolean authValid = session.getId().equals(sessionId) ? true : false;
             int index = skippablesMessages.indexOf(msg);
             String service = headers.optString("request_svc");
             JSONObject requestData = (JSONObject)obj.optJSONObject("request_object");
             AsyncContext ctx = request.startAsync(request, response);
             ctx.setTimeout(120000);
             ClientWorker worker = new ClientWorker(msg, service, requestData, session, ctx,response);
             if(index >- 1 && skippablesServices.get(index).equals(service)){
                authValid = true;
             }
             else if(!applicationIsValid){
                 Logger.getLogger(ServerLink.class.getName()).log(Level.SEVERE, "Application is not activated or has expired", (Object[])null);
                 worker.setResponseData(new RuntimeException("Application is not activated or has expired"));
                 server.exceptionToClient(worker);
                 return;
             }
             
             /*
             if(servicesSubscribedFor.indexOf(service) == -1 && skippablesServices.indexOf(service)==-1){
                //requests to unsubscribed services will not work 
                 Logger.getLogger(ServerLink.class.getName()).log(Level.SEVERE, "Request made to unsubscribed service: "+service, (Object[])null);
                 worker.setResponseData(new RuntimeException("Request made to unsubscribed service: "+service));
                 server.exceptionToClient(worker);
                 return;
                 
             }
             */
             HashMap services = Server.getServices();
             String privState = "";
             if(services != null){
                ArrayList serviceList = (ArrayList) services.get(service);
                if(serviceList != null){
                   privState = serviceList.get(2).toString();
                }
             }
             if(authValid || privState.equals("no")){
                ctx.start(worker);   
             }
             else{
                sendMessage(response);
                ctx.complete();
             }
        } catch (JSONException ex) {
            Logger.getLogger(ServerLink.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void sendMessage(HttpServletResponse response){
        try {
            JSONObject object=new JSONObject();  
            object.put("request_msg","auth_required");
            object.put("data","to use this service you need a valid auth token");
            response.getWriter().print(object);
        } catch (Exception ex) {
            Logger.getLogger(ServerLink.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
 
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     * @return a String containing servlet description
     */ 
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
    
    @Override 
    public void init(){
         ServletConfig config = getServletConfig();
         String dbName=config.getInitParameter("database-name");
         String uName=config.getInitParameter("database-username");
         String pass=config.getInitParameter("database-password");
         String host=config.getInitParameter("database-host");
         String passExpires=config.getInitParameter("password-expires");
         String maxRetries=config.getInitParameter("max-password-retries");
         String clientTimeout=config.getInitParameter("client-timeout");
         String mLogin=config.getInitParameter("multiple-login");
         String eDir=config.getInitParameter("extension-dir");
         String defPass=config.getInitParameter("default-password");
         Launcher.setDatabaseConnection(uName, host, pass);
         try {
            server = new Server(dbName); 
            server.setPassWordLife(Integer.parseInt(passExpires));
            server.setMaxPasswordAttempts(Integer.parseInt(maxRetries));
            server.setClientTimeout(Double.parseDouble(clientTimeout));
            server.setMultipleLoginState(Boolean.parseBoolean(mLogin));
            server.setExtensionDir(eDir);
            server.setConfig(config);
            server.setDefaultPassWord(defPass);
            server.createNativeServices();
            server.startAllServices();
            
            Database db=Database.getExistingDatabase("school_data");
            JSONObject data = db.query("SELECT USER_EMAIL, ACTIVATION_KEY FROM ACTIVATION_DATA");
            String activationKey = (String) data.optJSONArray("ACTIVATION_KEY").opt(0);
            String email= (String) data.optJSONArray("USER_EMAIL").opt(0);
            if(activationKey==null){
               applicationIsValid=false; 
               Logger.getLogger(ServerLink.class.getName()).log(Level.SEVERE, "Application is not activated or has no valid key", (Object[])null);
            }
            else{
              Object[] validateKey = validateKey(email, activationKey);
              if(validateKey==null){
                applicationIsValid=false;
                Logger.getLogger(ServerLink.class.getName()).log(Level.SEVERE, "Application has expired", (Object[])null); 
                return;
              }
              Long expiry=Long.parseLong((String)validateKey[0]);
              if(expiry<System.currentTimeMillis()){
                applicationIsValid=false;
                Logger.getLogger(ServerLink.class.getName()).log(Level.SEVERE, "Application has expired", (Object[])null);  
              }
              else{
                applicationIsValid=true;
                servicesSubscribedFor=(ArrayList) validateKey[1];
                if(servicesSubscribedFor.indexOf("mark_service")>-1){
                    servicesSubscribedFor.add("edit_mark_service");
                }
                if(servicesSubscribedFor.indexOf("student_service")>-1){
                    servicesSubscribedFor.add("edit_student_service");
                }
                servicesSubscribedFor.add("user_service");
                servicesSubscribedFor.add("privilege_service");
                servicesSubscribedFor.add("question_service");
              }
            }
        } catch (Exception ex) {
             try {
                 System.out.println(ex);
                 Thread.sleep(20000);
                 init();
             } catch (InterruptedException ex1) {
                 Logger.getLogger(ServerLink.class.getName()).log(Level.SEVERE, null, ex1);
             }
      } 
    } 

  
    public Object[] validateKey(String eAddress, String aKey){
        Object[] activeData = decodeKey(aKey, eAddress);
        if(activeData==null){
           return null;
        }
        else{
           return new Object[]{activeData[0]+"00000",activeData[1]};
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
		 String genServicesHash=com.quest.school.Security.toBase64(com.quest.school.Security.makePasswordDigest(dummy[x], secret)).substring(0,7);
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
		String accountHash=com.quest.school.Security.toBase64(com.quest.school.Security.makePasswordDigest(email, secret)).substring(0,6);
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
   
    
   
    
  
    
    
     
 
    public static Server getServerInstance(){
        return server;
    }
}
