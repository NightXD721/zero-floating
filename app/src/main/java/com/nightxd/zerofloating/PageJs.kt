package com.nightxd.zerofloating

object PageJs {
    val INSTALL: String = """(function(){
if(window.__zero)return;
var Z=window.__zero={n:[],o:[],t:[],m:[]};
var BAD=/^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA|INPUT|OPTION|SELECT|CODE|PRE|SVG|TITLE|HEAD)${'$'}/i;
function cov(rs,a,b){
var s=rs.slice().sort(function(p,q){return p[0]-q[0];});
var c=0,e=a;
for(var i=0;i<s.length;i++){
var x=Math.max(s[i][0],e),y=Math.min(s[i][1],b);
if(y>x){c+=y-x;e=y;}
}
return c;
}
Z.scan=function(budget){
var out=[];
var root=document.body;
if(!root)return out;
var vh=window.innerHeight||800;
var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null);
var c=[];
var idx=0;
var x;
while((x=w.nextNode())){
idx++;
if(x.__z)continue;
var p=x.parentNode;
if(!p||BAD.test(p.nodeName))continue;
var s=x.nodeValue;
if(!s||!/\p{L}/u.test(s))continue;
if(!p.getBoundingClientRect)continue;
var r=p.getBoundingClientRect();
if(r.width===0&&r.height===0)continue;
if(r.bottom<-vh||r.top>vh*3)continue;
var d=r.bottom<0?-r.bottom*1.5:(r.top>vh?r.top-vh:0);
c.push([d,x,idx]);
}
c.sort(function(a,b){return a[0]-b[0]||a[2]-b[2];});
var used=0;
for(var i=0;i<c.length;i++){
var n=c[i][1];
var t=n.nodeValue.replace(/^\s+|\s+${'$'}/g,'');
if(t.length<2){n.__z=1;continue;}
if(used+t.length>budget&&out.length>0)break;
n.__z=1;
var id=Z.n.length;
Z.n.push(n);
Z.o.push(n.nodeValue);
out.push([id,t]);
used+=t.length;
}
return out;
};
Z.scanAll=function(budget){
var out=[];
var root=document.body;
if(!root)return out;
var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null);
var c=[];
var idx=0;
var x;
while((x=w.nextNode())){
idx++;
if(x.__z)continue;
var p=x.parentNode;
if(!p||BAD.test(p.nodeName))continue;
var s=x.nodeValue;
if(!s||!/\p{L}/u.test(s))continue;
if(!p.getBoundingClientRect)continue;
var r=p.getBoundingClientRect();
if(r.width===0&&r.height===0)continue;
c.push([r.top+window.scrollY,x,idx]);
}
c.sort(function(a,b){return a[0]-b[0]||a[2]-b[2];});
var used=0;
for(var i=0;i<c.length;i++){
var n=c[i][1];
var t=n.nodeValue.replace(/^\s+|\s+${'$'}/g,'');
if(t.length<2){n.__z=1;continue;}
if(used+t.length>budget&&out.length>0)break;
n.__z=1;
var id=Z.n.length;
Z.n.push(n);
Z.o.push(n.nodeValue);
out.push([id,t]);
used+=t.length;
}
return out;
};
Z.put=function(a){
for(var k=0;k<a.length;k++){
var n=Z.n[a[k][0]];
if(!n||!n.parentNode)continue;
var m=n.nodeValue.match(/^(\s*)([\s\S]*?)(\s*)${'$'}/);
n.nodeValue=m[1]+a[k][1]+m[3];
Z.t[a[k][0]]=1;
}
};
Z.un=function(a){
for(var k=0;k<a.length;k++){
var n=Z.n[a[k]];
if(n)n.__z=0;
}
};
Z.rs=function(a){
for(var k=0;k<a.length;k++){
var id=a[k];
var n=Z.n[id];
if(!n)continue;
if(Z.t[id]&&n.parentNode){n.nodeValue=Z.o[id];Z.t[id]=0;}
n.__z=0;
}
};
Z.ri=function(){
var a=document.images;
for(var i=0;i<a.length;i++)a[i].__zr=null;
var b=document.getElementsByTagName('canvas');
for(var j=0;j<b.length;j++)b[j].__zr=null;
};
Z.back=function(){
for(var i=0;i<Z.n.length;i++){
var n=Z.n[i];
if(!n)continue;
if(Z.t[i]&&n.parentNode)n.nodeValue=Z.o[i];
n.__z=0;
}
Z.t=[];
Z.ri();
};
Z.im=function(){
var vv=window.visualViewport;
var ox=vv?vv.offsetLeft:0;
var oy=vv?vv.offsetTop:0;
var vw=vv?vv.width:window.innerWidth;
var vh=vv?vv.height:window.innerHeight;
var els=[];
var a=document.images;
for(var i=0;i<a.length;i++)els.push(a[i]);
var b=document.getElementsByTagName('canvas');
for(var j=0;j<b.length;j++)els.push(b[j]);
var out=[];
for(var k=0;k<els.length&&out.length<3;k++){
var e=els[k];
if(e.tagName==='IMG'&&!e.complete)continue;
var r=e.getBoundingClientRect();
if(r.width<150||r.height<100)continue;
var left=Math.max(r.left,ox);
var top=Math.max(r.top,oy);
var right=Math.min(r.right,ox+vw);
var bottom=Math.min(r.bottom,oy+vh);
var w=right-left;
var h=bottom-top;
if(w<100||h<60)continue;
var ya=top-r.top;
var yb=bottom-r.top;
var rs=e.__zr||(e.__zr=[]);
if(cov(rs,ya,yb)>=(yb-ya)*0.7)continue;
rs.push([ya,yb]);
Z.m.push(e);
out.push([Z.m.length-1,left-ox,top-oy,w,h,ya,yb]);
}
return {vw:vw,vh:vh,list:out};
};
Z.uf=function(id,ya,yb){
var e=Z.m[id];
if(!e||!e.__zr)return;
for(var i=e.__zr.length-1;i>=0;i--){
if(e.__zr[i][0]===ya&&e.__zr[i][1]===yb){e.__zr.splice(i,1);break;}
}
};
})();
"""
}
