package com.quest.access.useraccess.verification;

import com.quest.access.common.UniqueRandom;
import com.quest.access.common.mysql.Database;
import com.quest.access.control.Server;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

/**
 *
 * @author constant oduol
 * @version 1.0(23/6/12)
 */

/**
 * This class tries to store information concerning actions of users in the system
 * this is done using a verification process where users with the relevant privileges
 * or relevant authority can carry out actions in the system.
 * @author Conny
 */
  public class SystemAction implements Action {  
      private String actionID;
      private Server serv;
      private String description;  
      public SystemAction(Server serv, String description) throws Exception{
                UniqueRandom ur=new UniqueRandom(50);
                actionID=ur.nextMixedRandom();
                this.serv=serv;
                this.description=description;
             
     }   
      /**
       * 
       * @return a string representing the id of this action
       */
    @Override
      public String getActionID(){
          return this.actionID;
      }
      
      /**
       * this method is called to commit an action as performed by a specific user
       */
    @Override
      public void saveAction(){
          String dbName=this.serv.getDatabase().getDatabaseName();
          Database.executeQuery("INSERT INTO USER_ACTIONS"
                  + " VALUES(?,?,?,NOW(),?)", dbName, this.actionID,"SYSTEM_000","SYSTEM_000",this.description);
      }
      
      /**
       * this method is called to get details concerning a specific action is string
       */
      public static HashMap getActionDetails(String actionId,Server serv){
        ResultSet set = Database.executeQuery("SELECT * FROM USER_ACTIONS WHERE ACTION_ID=?", serv.getDatabase().getDatabaseName(),actionId);
        HashMap details=new HashMap();
          try {
            while(set.next()){
              details.put("USER_ID",set.getString("USER_ID"));
              details.put("ACTION_TIME",set.getString("ACTION_TIME"));
              details.put("USER_NAME",set.getString("USER_NAME"));
              details.put("ACTION_DESCRIPTION",set.getString("ACTION_DESCRIPTION"));
            }
            set.close();
            return details;
        } catch (SQLException ex) {
          return details;
        }
      }
      
}
