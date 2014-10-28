var loader={
      start:function (loadId,width,height,delay){
	       var pos=document.getElementById(loadId); 
		   var id=loadId+Math.floor(1000*Math.random());
		   if(delay==null){
		     delay=100;
			}
		   animate();
	       function animate(){ 
		      pos.innerHTML="";
			  var table=document.createElement("table");
			  table.setAttribute("width",width);
			  table.setAttribute("height",height);
			  table.setAttribute("cellspacing",0);
			  var tr=document.createElement("tr");
			  tr.setAttribute("id",id);
			  var td1=document.createElement("td");
			  var td2=document.createElement("td");
			  var td3=document.createElement("td");
			  var td4=document.createElement("td");
		      var td5=document.createElement("td");
			  var td6=document.createElement("td");
			  tr.appendChild(td1);
			  tr.appendChild(td2);
			  tr.appendChild(td3);
			  tr.appendChild(td4);
			  tr.appendChild(td5);
			  tr.appendChild(td6);
			  table.appendChild(tr);
			  pos.appendChild(table);
			  generateRandom(id,loadId);
			 }
			 
		function refreshAnimation(id,loadId){
			var timeOut=setTimeout(
			    function(){
			       generateRandom(id,loadId);
			     }
			 ,delay
			 ); 
			document.getElementById(loadId).setAttribute("timeout",timeOut);
		 }
			 
		 function generateRandom(id,loadId) {
		      var rands=[];
			  for(var x=0; x<18; x++){
			    rands.push(getRand()); 
			  }
			  var pos=document.getElementById(id) 
			  var x=0;
			  var child = pos.firstChild;
              while(child){
                if(child.nodeName.toLowerCase() == 'td'){
				    child.setAttribute("bgcolor",rands[x]);
                }
               child = child.nextSibling;
			   x++;
             }
			refreshAnimation(id,loadId);
		  }
		
		function getRand(){
		   return  "#"+((1<<24)*Math.random()|0).toString(16);
		}
		 
			
	   },
	   
	   stop:function(id){
	     var timeOut= document.getElementById(id).getAttribute("timeout");
		 clearTimeout(timeOut);
	     document.getElementById(id).innerHTML="";
	   }
	   



}