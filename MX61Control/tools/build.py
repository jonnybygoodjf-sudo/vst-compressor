import json, re
cat=json.load(open('gen/cat.json'))
tables=json.load(open('gen/tables.json'))
tl=json.load(open('gen/typelist.json'))
# normalize names between catalog and type list
alias={'DELAY LR':'DELAY L, R','DELAY LCR':'DELAY L, C, R','DELAY LR (Stereo)':'DELAY L, R (STEREO)','NOISE GATE+COMP+EQ':'NOISE GATE + COMP + EQ','THRU':'THRU'}
def num(s):
    m=re.match(r'^\s*([+-]?\d+(?:\.\d+)?)\s*([A-Za-z%/]*)',s)
    return (float(m.group(1)),m.group(2),len(m.group(1).split('.')[1]) if '.' in m.group(1) else 0) if m else None
bad=[]
def disp(p, tname):
    rng=p['range']; vmin,vmax=p['vmin'],p['vmax']; tb=p['table']
    if tb:
        if '/' in tb: tb = '20' if 'HALL' in tname else '21'
        return {'t':int(tb)}
    if '>' in rng and '=' in rng:
        a=rng.split(' – ')[0]
        return {'k':'bal','l':a.split('>')[0][0],'r':a.split('>')[1][0]}
    if re.match(r'^L63',rng) and 'R63' in rng: return {'k':'lin','lo':-63.0,'hi':63.0,'u':'','d':0}
    if ',' in rng and '–' not in rng:
        return {'k':'enum','v':[x.strip() for x in rng.split(',')]}
    parts=[x.strip() for x in rng.split(' – ')]
    if len(parts)==2:
        a,b=num(parts[0]),num(parts[1])
        if a and b:
            u=b[1] or a[1]
            return {'k':'lin','lo':a[0],'hi':b[0],'u':u,'d':max(a[2],b[2])}
    parts=[x.strip() for x in re.split(r'\s*–\s*',rng)]
    if len(parts)==2:
        a,b=num(parts[0]),num(parts[1])
        if a and b: return {'k':'lin','lo':a[0],'hi':b[0],'u':b[1] or a[1],'d':max(a[2],b[2])}
    m=re.match(r'^(\d+)\s*[–-]\s*(\d+)$',rng.replace(' ',''))
    if m: return {'k':'lin','lo':float(m.group(1)),'hi':float(m.group(2)),'u':'','d':0}
    bad.append((tname,p['name'],rng)); return {'k':'raw'}
def plist(ps,tname,block,crossdelay):
    out=[]
    for p in ps:
        if 'same' in p:
            a,b=p['same']
            for i,no in enumerate(range(a,b+1)):
                src=[q for q in crossdelay if q.get('no')==13+i][0]
                q=dict(src); q['no']=no; out.append(q)
        else: out.append(p)
    res=[]
    for p in out:
        if 'same' in p: continue
        d=disp(p,tname)
        name=re.sub(r'\s*\(\*\d\)','',p['name']).strip()
        e={'no':p['no'],'name':name,'min':p['vmin'],'max':p['vmax'],'d':d}
        if name in('Dry/Wet Balance','Mix') : e['dw']=1
        if block=='cho' and name=='Density': e['max']=2; e['d']=dict(d,hi=2.0)
        if d.get('k')=='enum' and d['v']==['High','Mid','Low Bass']: e['d']={'k':'enum','v':['High','Mid','Low','Bass']}
        res.append(e)
    return res
cross=cat['cho']['CROSS DELAY']
effects=[]
for name,msb,lsb,flags in tl:
    cname=alias.get(name,name)
    e={'name':name.title() if False else name,'msb':int(msb,16),'lsb':int(lsb,16),'rev':flags['REV'],'cho':flags['CHO'],'ins':flags['INS']}
    if name in('NO EFFECT','THRU'): e['params']={}
    else:
        pr={}
        if flags['REV'] and cname in cat['rev']: pr['rev']=plist(cat['rev'][cname],cname,'rev',cross)
        if (flags['CHO'] or flags['INS']) and cname in cat['cho']: pr['cho']=plist(cat['cho'][cname],cname,'cho',cross)
        if not pr: print('NO PARAMS',name)
        e['params']=pr
    effects.append(e)
tabs={str(k):[v['vals'].get(str(i),'?') for i in range(max(map(int,v['vals']))+1)] for k,v in tables.items()}
for k,v in tabs.items():
    if '?' in v: print('hole in table',k,[i for i,x in enumerate(v) if x=='?'][:5])
json.dump({'tables':tabs,'effects':effects},open('gen/effects.json','w'),ensure_ascii=False,separators=(',',':'))
print('bad',bad)
import os; print(os.path.getsize('gen/effects.json'))
