var iwidth="1250";
var iheight="800";

var tabs=new TabMap();
tabs.names[0]="Users";
tabs.links[0]="user.html";
tabs.names[1]="Privileges";
tabs.links[1]="privilege.html";
tabs.names[2]="Services";
tabs.links[2]="service.html";
tabs.names[3]="Preset Groups";
tabs.links[3]="preset.html";
tabs.names[4]="Sessions";
tabs.links[4]="session.html";
tabs.names[5]="Log Out";
tabs.links[5]="log_out_link";
Array.prototype.indexOf=indexOf;
var currentTab;

var loadedTabs=[];

function indexOf(value){
    for(x=0; x<this.length; x++){
	     if(this[x]==value){
		    return x;
		  }
	  }
	  return -1;
	   
  }

function TabMap(){
 this.names=[];
 this.links=[];
}

 function loadTabs(){
   var appview="<div id='ddtabs4' class='ddcolortabs'><ul>"
    for(x=0;x<tabs.names.length; x++){
	  var link="\""+tabs.links[x]+"\""
	  var href="<li><a href='javascript:loadTab("+link+")' id="+tabs.links[x]+"><span>"+tabs.names[x]+"</span></a></li>";
	   appview=appview+href;
		 }
	    appview=appview+"</ul></div><div class='ddcolortabsline'>&nbsp;</div>";
       document.getElementById("tabs").innerHTML=appview;
  }
  
    function loadTab(link){
	  if(currentTab==link){
	    return;
	  }
	  var frameLink="tabframe"+currentTab;
	  if(currentTab!=null){
	    hideTab(frameLink);
	  }
	  
	  if(loadedTabs.indexOf(link)>-1){
	    var fLink="tabframe"+link
	    resumeTab(fLink);
	    currentTab=link;
	  }
	  else{
	      var iframe=document.createElement("iframe");
	        iframe.id="tabframe"+link;
		  iframe.src=link
	          iframe.frameborder=0;
		  iframe.width=iwidth
		  iframe.height=iheight
		  document.getElementById("screen").appendChild(iframe);
		  currentTab=link;
		  loadedTabs.push(link)
	  }
 }
 
 
 function hideTab(frameLink){
     document.getElementById(frameLink).style.display="none"
     
 }
 
 function resumeTab(frameLink){
     document.getElementById(frameLink).style.display="block"
 }
 
 
 function getTabMap(){
     
 }
 
