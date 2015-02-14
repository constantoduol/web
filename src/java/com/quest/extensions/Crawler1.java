/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.quest.extensions;

import com.quest.access.common.UniqueRandom;
import com.quest.access.common.mysql.Database;
import com.quest.access.common.mysql.NonExistentDatabaseException;
import com.quest.access.control.Server;
import com.quest.access.useraccess.Serviceable;
import com.quest.access.useraccess.services.annotations.Endpoint;
import com.quest.access.useraccess.services.annotations.Model;
import com.quest.access.useraccess.services.annotations.Models;
import com.quest.access.useraccess.services.annotations.WebService;
import com.quest.servlets.ClientWorker;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

/**
 *
 * @author connie
 */

@WebService (name = "search_service", level = 10, privileged = "no")
@Models(models = {
    @Model(
       database = "search_data", table = "INDEX_HISTORY",
       columns = {"FILE_PATH VARCHAR(512) PRIMARY KEY", 
                  "FILE_NAME TEXT",
                  "MODIFIED LONG",
                  "FILE_SIZE TEXT",
                  "HIDDEN BOOL",
                  "CRAWL_ID TEXT"
                  
       })
    }
)
public class Crawler1 implements Serviceable {
    
  private static int fileCount = 0;
  
  private static boolean stopCrawl = false;
  
  private static Database db;
  
  private static String crawlId;
  
  @Endpoint(name ="init_crawler")
  public void crawl(Server serv,ClientWorker worker){
      UniqueRandom rand = new UniqueRandom(10);
      crawlId = rand.nextMixedRandom();
      File[] fileRoots = File.listRoots();
      for(File fileRoot : fileRoots){
        searchFiles(fileRoot.listFiles());
      }
  }
  
  @Endpoint(name ="stop_crawler")
  public void stopCrawl(Server serv,ClientWorker worker){
     stopCrawl = true;
  }
  
  
  
  
  private static void searchFiles(File[] files){
      if(files == null || stopCrawl){
          return;
      }
      for(File file : files){
          fileCount++;
          if(fileCount % 50 == 0){
              System.out.print(".");
          }
          if(file.isDirectory()){
            // System.out.println(file.getAbsolutePath());
            //this is a directory
              searchFiles(file.listFiles());
          }
          else {
              insertFile(file);
          }
      }
  }
  
    private static void insertFile(File file){
        String fileName = file.getName();
        String filePath = file.getAbsolutePath();
        Long lastMod = file.lastModified();
        Long size = file.length();
        String hidden = file.isHidden() == true ? "1" : "0";
        boolean existInDb = db.ifValueExists(filePath,"INDEX_HISTORY","FILE_PATH");
        if(existInDb){
           db.query().update("INDEX_HISTORY").set("CRAWL_ID='"+crawlId+"'").where("FILE_PATH='"+filePath+"'").execute();
        }  
        else {
            db.doInsert("INDEX_HISTORY",new String[]{filePath,fileName,lastMod.toString(),size.toString(),hidden,crawlId});
        }
    }
    
    private JSONObject fetchFiles(String name,Integer limit){
       JSONObject data = db.query().select("*")
               .from("INDEX_HISTORY")
               .where("FILE_NAME LIKE '%"+name+"%'")
               .limit(limit.toString())
               .execute();
       return data;
    }
    
    
    @Endpoint(name = "do_search")
    public void doSearch(Server serv, ClientWorker worker){
        JSONObject data = worker.getRequestData();
        Integer limit = data.optInt("limit");
        String name = data.optString("search");
        JSONObject fileData = fetchFiles(name,limit);
        worker.setResponseData(fileData);
        serv.messageToClient(worker);
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
          db = Database.getExistingDatabase("search_data");
      } catch (NonExistentDatabaseException ex) {
          Logger.getLogger(Crawler1.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
}


