/**
 * this file uses ajax to communicate with a mysql server, this file enables a user to
 * send sql commands to the mysql server using javascript
 */

var Database={
    
 executeQuery : function(dbName,user,sql,request,callback){
    var req_header={
        "quest_msg" : "query",
        "quest_svc" : "js_service",
        "current_user" : user
    }
    var req_obj={
       "db_name" : dbName,
       "sql": sql
    } 
    
    var json={
        "request_header" :req_header,
        "request_object" : req_obj
    } 
    sendJSON("/QuestWeb/QuestServlet", json, request, callback)
 }  
 
 
    
    
    
    
}


function getRequestObject(){
    if(window.ActiveXObject){
      return new ActiveXObject("Microsoft.XMLHTTP");  
    }
    else if(window.XMLHttpRequest){
       return new  XMLHttpRequest();
    }
    else{
       return null; 
    }
    
}

function sendJSON(serverUrl,json,request,callback){
       json="json="+JSON.stringify(json);
       request.onreadystatechange=callback
       request.open("POST", serverUrl, true);
       request.setRequestHeader("Content-Type","application/x-www-form-urlencoded");
       request.send(json);   
}


