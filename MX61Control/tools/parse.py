import re, json
lines=[]
for p in range(33,41):
    for side in 'LR':
        lines += open(f'{side}{p}.txt').read().split('\n')
hdr=re.compile(r'^\s*\[(\d+)\]\s+(.+?)\s*$')
row=re.compile(r'^\s*(\d+)\s+(\S.*?)\s{2,}(\S.*?)\s+\((\d+)\s*–\s*(\d+)\)\s*(\d+(?:/\d+)?)?\s*$')
dash=re.compile(r'^\s*(\d+)\s+—\s*$')
same=re.compile(r'^\s*(\d+)\s*–\s*(\d+):\s*Same as')
cat={'rev':{}, 'cho':{}}
block='rev'; pending=[]; cur=None; inTable=False
miss=[]
for s in lines:
    s=s.rstrip()
    if s.startswith('Chorus, Insertion Block'): block='cho'; pending=[]; inTable=False; continue
    if 'When this type is selected' in s: pending=[]; inTable=False; continue
    m=hdr.match(s)
    if m and not row.match(s):
        if inTable: pending=[]; inTable=False
        pending.append(m.group(2).strip()); continue
    if s.strip().startswith('No.') and 'Parameter' in s:
        inTable=True; cur=[]
        for n in pending: cat[block][n]=cur
        continue
    if not inTable: continue
    m=row.match(s)
    if m:
        no,name,rng,a,b,tb=m.groups()
        cur.append(dict(no=int(no),name=name.strip(),range=rng.strip(),vmin=int(a),vmax=int(b),table=tb)); continue
    if dash.match(s): continue
    m=same.match(s)
    if m: cur.append(dict(same=[int(m.group(1)),int(m.group(2))])); continue
    st=s.strip()
    if st and cur and cur[-1].get('range','').rstrip().endswith(',') and not st.startswith(('*','(','Category','No.')):
        cur[-1]['range']=cur[-1]['range']+' '+st; continue
    if re.match(r'^\s*\d+\s',s): miss.append(s)
json.dump(cat,open('gen/cat.json','w'),indent=1,ensure_ascii=False)
for b in cat:
    print('==',b)
    for k,v in cat[b].items(): print(' ',k,[x.get('no',x.get('same')) for x in v])
print('MISSED:'); print('\n'.join(miss))
