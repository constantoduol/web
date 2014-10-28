/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.quest.school;

import com.quest.access.common.io;
import java.util.StringTokenizer;

/**
 *
 * @author connie
 */
public class Test {
    public static void main(String [] args){
       String str = "all_marks";
       StringTokenizer st = new StringTokenizer(str,",");
       int x = 0;
       for( ; st.hasMoreTokens(); x++){
          String service = st.nextToken();
          io.out(service);
       }
       io.out(x);
      
    }
}
