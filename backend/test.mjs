// HTTP contract tests. Provider responses below are explicitly simulated, not an AI/camera validation.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {createBackend} from './server.mjs';
const token='a'.repeat(64), other='b'.repeat(64), hash=x=>createHash('sha256').update(x).digest('hex');
test('authenticated session contract, consent, privacy, limits, failure and deletion',async()=>{
 let requests=[];let failure=false;let time=100000;
 const server=createBackend({deviceHashes:[hash(token),hash(other)],apiKey:'TEST-ONLY',now:()=>time,
  fetcher:async(url,options)=>{requests.push({url,options});if(failure)return{ok:false};return{ok:true,json:async()=>url.includes('/audio/')?{text:'voda'}:url.endsWith('/responses')?{output:[{content:[{type:'output_text',text:'{"event":"head_turn","uncertainty":true}'}]}]}:{id:'fixture'}}}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const base=`http://127.0.0.1:${server.address().port}`;
 const call=async(path,body={},key=token,method='POST')=>{const r=await fetch(base+path,{method,headers:{Authorization:`Bearer ${key}`,'Content-Type':'application/json'},body:method==='DELETE'?undefined:JSON.stringify(body)});return{status:r.status,json:await r.json()};};
 try{
  assert.equal((await call('/v1/health',{},'invalid')).status,401);
  assert.equal((await call('/v1/health')).json.ready,true);
  assert.equal((await call('/v1/health')).status,200);
  assert.equal((await call('/v1/sessions',{language:'sl'})).status,400);
  const s=await call('/v1/sessions',{consent:true,language:'sl',microphone:true,camera:false});assert.equal(s.status,201);
  const path='/v1/sessions/'+s.json.id;
  assert.equal((await call('/v1/sessions',{consent:true,language:'sl'})).status,409);
  assert.equal((await call(path+'/camera',{startMs:0,endMs:1000},s.json.token)).status,403);
  assert.equal((await call(path+'/audio',{startMs:-1,endMs:1000},s.json.token)).status,400);
  const wav=Buffer.alloc(48);wav.write('RIFF');wav.write('WAVE',8);wav.writeUInt16LE(1,20);wav.writeUInt16LE(1,22);wav.writeUInt32LE(16000,24);wav.writeUInt16LE(16,34);
  const body={startMs:0,endMs:12000,appSpeechOverlap:false,wav:wav.toString('base64')};
  assert.equal((await call(path+'/audio',{...body,appSpeechOverlap:true},s.json.token)).status,400);
  const transcript=await call(path+'/audio',body,s.json.token);
  assert.equal(transcript.json.text,'voda');assert.equal(transcript.json.speaker,'unknown');assert.equal(transcript.json.uncertain,true);assert.equal(transcript.json.confirmation,'pending');
  assert.equal(requests.at(-1).options.body.get('model'),'gpt-transcribe');
  assert.deepEqual(requests.at(-1).options.body.getAll('languages[]'),['sl','uk']);
  assert.equal((await call(path,{},other,'DELETE')).status,403);
  failure=true;assert.equal((await call(path+'/audio',body,s.json.token)).status,502);failure=false;
  assert.equal((await call(path,{},s.json.token,'DELETE')).json.rawStored,false);
  assert.equal((await call(path+'/audio',body,s.json.token)).status,401);
  const c=await call('/v1/sessions',{consent:true,language:'uk',camera:true});
  const cp='/v1/sessions/'+c.json.id;const jpg=Buffer.from([255,216,255,217]).toString('base64');
  const vision=await call(cp+'/camera',{startMs:0,endMs:1000,frames:[jpg,jpg,jpg]},c.json.token);
  assert.equal(vision.json.text,'head_turn');assert.equal(vision.json.uncertain,true);
  const providerBody=JSON.parse(requests.at(-1).options.body);assert.equal(providerBody.store,false);assert.equal(providerBody.input[0].content.length,4);
  const replaced=await call('/v1/sessions',{consent:true,language:'uk',camera:true,replace:true});assert.equal(replaced.status,201);
  assert.equal((await call(cp+'/camera',{},c.json.token)).status,401);
  time+=1800001;assert.equal((await call(cp+'/camera',{},c.json.token)).status,401);
 }finally{await new Promise(r=>server.close(r));}
});
test('no configured provider never reports ready',async()=>{
 const server=createBackend({deviceHashes:[hash(token)],apiKey:''});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 try{const r=await fetch(`http://127.0.0.1:${server.address().port}/v1/health`,{method:'POST',headers:{Authorization:`Bearer ${token}`}});assert.equal(r.status,503);}
 finally{await new Promise(r=>server.close(r));}
});
test('stop aborts an in-flight provider request and invalidates the session token',async()=>{
 let observed;const started=new Promise(r=>observed=r);let aborted=false;
 const server=createBackend({deviceHashes:[hash(token)],apiKey:'TEST-ONLY',fetcher:async(_,options)=>{
  observed();return new Promise((resolve,reject)=>{options.signal.addEventListener('abort',()=>{aborted=true;reject(new Error('aborted'));},{once:true});});
 }});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
 const call=(path,key,body,method='POST')=>fetch(base+path,{method,headers:{Authorization:`Bearer ${key}`,'Content-Type':'application/json'},body:method==='DELETE'?undefined:JSON.stringify(body)});
 try{
  const s=await(await call('/v1/sessions',token,{consent:true,language:'sl',camera:true})).json();const path='/v1/sessions/'+s.id;
  const jpg=Buffer.from([255,216,255,217]).toString('base64');
  const pending=call(path+'/camera',s.token,{startMs:0,endMs:1000,frames:[jpg,jpg,jpg]});
  await started;assert.equal((await call(path,s.token,null,'DELETE')).status,200);
  assert.equal((await pending).status,502);assert.equal(aborted,true);
  assert.equal((await call(path+'/camera',s.token,{})).status,401);
 }finally{await new Promise(r=>server.close(r));}
});
