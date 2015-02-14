/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.quest.school;

import com.quest.access.common.io;
import com.quest.access.useraccess.Serviceable;
import com.quest.access.useraccess.services.annotations.WebService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author connie
 */

@WebService(name="quest_test_service")
public class Test implements Serviceable {
    
    private static String auth_token = "";
    
    
    public static JSONObject makeRequest(String msg,String svc,JSONObject data){
        try {
            JSONObject message = new JSONObject();
            JSONObject reqHeaders = new JSONObject();
            reqHeaders.put("request_msg",msg);
            reqHeaders.put("request_svc",svc);
            reqHeaders.put("session_id",auth_token);
            message.put("request_header",reqHeaders);
            message.put("request_object",data);
            JSONObject response = InternetMessageService.sendRemoteData(message, "http://127.0.0.1:8080/web/server");
            return response;
        } catch (JSONException ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    @Override
    public void service() {
      
    }

    @Override
    public void onCreate() {
        
    }

    @Override
    public void onStart() {
        try {
            System.out.println("test service starting");
            JSONObject loginData = new JSONObject();
            loginData.put("username","root");
            loginData.put("password","pasnipop1");
            JSONObject loginResponse = makeRequest("login","", loginData);
               // JSONObject resp = makeRequest("read_attendance","class_service", subData);
            auth_token = loginResponse.optJSONObject("response").optString("rand");
            io.out("api authorization : "+auth_token);
        } catch (Exception ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
