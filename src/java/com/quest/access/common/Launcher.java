
package com.quest.access.common;

import com.quest.access.common.mysql.Database;

/**
 *
 * @author constant oduol
 * @version 1.0(4/1/2012)
 */

/**
 * this class has static methods for launching a server
 * database connections and server configuration details are handled by
 * this class
 */
public abstract class Launcher{     
     public static void setDatabaseConnection(String userName, String host ,String password){
         Database.setDefaultConnection(userName, host, password);
     }
}
