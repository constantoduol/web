/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.quest.school;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author connie
 */
public class Test {
    public static void main(String [] args){
        try {
            JSONObject loginData = new JSONObject();
            loginData.put("username","root");
            loginData.put("password","pasnipop1");
            JSONObject loginResponse = makeRequest("login","", loginData);
            JSONObject subData = new JSONObject();
            makeRequest("all_subjects","student_service", subData);
        } catch (JSONException ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        }
       
    }
    
    public static JSONObject makeRequest(String msg,String svc,JSONObject data){
        try {
            JSONObject message = new JSONObject();
            JSONObject reqHeaders = new JSONObject();
            reqHeaders.put("request_msg",msg);
            reqHeaders.put("request_svc",svc);
            message.put("request_header",reqHeaders);
            message.put("request_object",data);
            JSONObject response = InternetMessageService.sendRemoteData(message, "http://127.0.0.1:8080/web/server");
            return response;
        } catch (JSONException ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
}
