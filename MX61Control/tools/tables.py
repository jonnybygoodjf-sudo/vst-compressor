import pdfplumber, re, json
pdf=pdfplumber.open('dl.pdf')
tables={}
for pn in range(41,53):
    page=pdf.pages[pn-1]
    words=page.extract_words(keep_blank_chars=False,use_text_flow=False)
    heads=[]
    for i,w in enumerate(words):
        if w['text']=='Table' and i+1<len(words) and re.match(r'#\d+$',words[i+1]['text']):
            heads.append((int(words[i+1]['text'][1:]),w['x0'],w['top']))
    # also header like "Table #1" may be single word "#1"
    for hn,hx,ht in heads:
        # right limit
        right=page.width
        for n2,x2,t2 in heads:
            if n2!=hn and x2>hx+20 and abs(t2-ht)<40: right=min(right,x2-2)
        bottom=page.height
        for n2,x2,t2 in heads:
            if n2!=hn and t2>ht+30 and x2<right-5 and x2>hx-150 and not (x2>=right): bottom=min(bottom,t2-2)
        # also "x2 within range"
        reg=[w for w in words if w['x0']>=hx-3 and w['x1']<=right+3 and ht-2<=w['top']<bottom]
        datax=sorted(w['x0'] for w in reg if w['text']=='Data')
        # cluster datax
        cols=[]
        for x in datax:
            if not cols or x-cols[-1]>15: cols.append(x)
        if not cols: continue
        title=' '.join(w['text'] for w in sorted([w for w in reg if w['top']<min(w['top'] for w in reg if w['text']=='Data')-1 and w['top']>ht+2],key=lambda w:(round(w['top']),w['x0'])))
        lines={}
        for w in reg:
            lines.setdefault(round(w['top']/3),[]).append(w)
        vals={}
        for k in sorted(lines):
            ws=sorted(lines[k],key=lambda w:w['x0'])
            for ci,cx in enumerate(cols):
                nx=cols[ci+1] if ci+1<len(cols) else 1e9
                chunk=[w for w in ws if cx-6<=w['x0']<nx-6]
                if chunk and re.fullmatch(r'\d+',chunk[0]['text']):
                    v=' '.join(w['text'] for w in chunk[1:])
                    if v: vals[int(chunk[0]['text'])]=v
        tables[hn]=dict(title=title,vals=vals,page=pn)
json.dump(tables,open('gen/tables.json','w'),ensure_ascii=False,indent=0)
for n in sorted(tables): 
    t=tables[n]; v=t['vals']; print(n,t['page'],t['title'][:40],'|',len(v),'min',min(v) if v else None,'max',max(v) if v else None)
