/* Rotina Fitness: funcionamento offline. Para atualizar o app, envie a pasta de novo e aumente o número da versão abaixo. */
const V='rotina-v9';
const ARQ=['./','index.html','manifest.webmanifest','icons/icon-192.png','icons/icon-512.png','icons/icon-maskable-512.png','icons/apple-touch-icon.png','icons/favicon-32.png'];
self.addEventListener('install',function(e){
  e.waitUntil(caches.open(V).then(function(c){return c.addAll(ARQ);}).then(function(){return self.skipWaiting();}));
});
self.addEventListener('activate',function(e){
  e.waitUntil(caches.keys().then(function(ks){return Promise.all(ks.filter(function(k){return k!==V;}).map(function(k){return caches.delete(k);}));}).then(function(){return self.clients.claim();}));
});
self.addEventListener('fetch',function(e){
  var r=e.request;
  if(r.method!=='GET')return;
  var u=new URL(r.url);
  if(r.mode==='navigate'){
    e.respondWith(fetch(r).then(function(res){
      if(res&&res.ok){var cp=res.clone();caches.open(V).then(function(c){c.put('index.html',cp);});}
      return res;
    }).catch(function(){
      return caches.match('index.html',{ignoreSearch:true}).then(function(x){return x||caches.match('./',{ignoreSearch:true});});
    }));
    return;
  }
  if(u.origin===location.origin){
    e.respondWith(caches.match(r,{ignoreSearch:true}).then(function(x){
      return x||fetch(r).then(function(res){
        if(res&&res.ok){var cp=res.clone();caches.open(V).then(function(c){c.put(r,cp);});}
        return res;
      });
    }));
    return;
  }
  if(u.hostname==='fonts.googleapis.com'||u.hostname==='fonts.gstatic.com'){
    e.respondWith(caches.open(V).then(function(c){
      return c.match(r).then(function(x){
        var f=fetch(r).then(function(res){c.put(r,res.clone());return res;}).catch(function(){return x;});
        return x||f;
      });
    }));
  }
});
