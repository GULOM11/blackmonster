# Black Monster v3 - 4 tomonlama (turnaround) rasm o'lchovlari asosida. Ishga tushirish: python tools/build_monster_model.py
import numpy as np, math, os, sys
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0,'tools')
from mesh_toolkit import ell, cap, smin, union, path, mesh, decimate, uv_box, write_obj, pnoise
def V(*a): return np.array(a,float)
HIP=V(0.27,1.67,0.25); KNEE=V(0.62,1.2,-0.29); ANK=V(0.5,0.37,0.59); HEEL=V(0.5,0.10,0.69); TOE=V(0.5,0.0,0.32)
SH=V(0.6,2.68,-0.126); EL=V(0.82,2.0,-0.09); WR=V(0.88,1.25,-0.85)
HEAD_C=V(0,2.92,-0.2); HEAD_R=V(0.27,0.48,0.5)
EYE=V(0.22,2.93,-0.53); ER=0.085
def lean(P,k=0.2): Q=P.copy(); Q[:,2]=Q[:,2]+k*np.clip(Q[:,1]-1.8,0,None); return Q  # ustki tana oldinga engashgan
HC=V(0,2.75,-0.05); TILT=math.radians(15)
def tilt(P):  # bosh oldinga-pastga egilgan (qalpoqsimon)
    Q=P-HC; c,s=math.cos(TILT),math.sin(TILT); R=np.stack([Q[:,0],Q[:,1]*c-Q[:,2]*s,Q[:,1]*s+Q[:,2]*c],1); return R+HC
def sx(P,k=1.25): Q=P.copy(); Q[:,0]=Q[:,0]/k; return Q
SPINE=[(0,1.62,0.33),(0,2.0,0.22),(0,2.45,0.12),(0,2.85,0.12)]
def sdf_torso(P):
    ds=[path(sx(P),SPINE,[0.30,0.24,0.42,0.38],0.12)]  # bitta uzluksiz bukri yoy
    for s in (1,-1):
        ds+=[ell(P,(0.6*s,2.7,0.03),(0.22,0.24,0.24)),ell(P,(0.3*s,3.0,0.12),(0.28,0.26,0.3)),ell(P,(0.3*s,2.75,0.4),(0.2,0.24,0.1)),ell(P,(0.17*s,1.5,0.52),(0.19,0.19,0.15)),
             path(P,[(0.11*s,1.8,0.6),(0.11*s,2.1,0.53),(0.11*s,2.45,0.53),(0.11*s,2.8,0.49),(0.11*s,3.02,0.33)],[0.09,0.09,0.1,0.1,0.09],0.05)]  # trapetsiya, kurak, dumba, umurtqa yonidagi mushaklar
    d=union(ds,0.14)
    d=-smin(-d,path(P,[(0,1.8,0.66),(0,2.1,0.59),(0,2.45,0.59),(0,2.8,0.55),(0,3.05,0.38)],[0.05]*5,0.02),0.04)  # chuqur umurtqa ariqchasi
    rib=np.clip((np.abs(P[:,0])-0.3)*5,0,1)*np.clip((2.6-P[:,1])*3,0,1)*np.clip((P[:,1]-2.1)*3,0,1)
    return d+0.007*np.sin(26*P[:,1])*rib
def sdf_head(P):
    d=smin(ell(P,tuple(HEAD_C),tuple(HEAD_R)),ell(P,(0,3.05,-0.12),(0.23,0.36,0.4)),0.1)  # baland tuxumsimon gumbaz
    d=smin(d,ell(P,(0,2.85,0.1),(0.24,0.3,0.3)),0.1)
    for s in (1,-1): d=smin(d,ell(P,(s*0.19,2.93,-0.5),(0.11,0.1,0.11)),0.05)  # ko'z qovoqlari/do'ngliklari
    d=smin(d,ell(P,(0,2.72,-0.6),(0.15,0.16,0.16)),0.08)  # og'iz ustiga osilgan tumshuq
    for s in (1,-1): d=smin(d,ell(P,(s*0.17,2.99,-0.55),(0.14,0.07,0.12)),0.05)  # og'ir qosh qirrasi
    d=np.maximum(d,-ell(P,(0,2.5,-0.48),(0.12,0.07,0.1)))  # pastga qaragan og'iz yorig'i
    for s in (1,-1): d=np.maximum(d,-(np.linalg.norm(P-V(s*EYE[0],EYE[1],EYE[2]),axis=1)-0.09))
    return d
def sdf_eyes(P): return np.minimum(ell(P,tuple(EYE),(ER,)*3),ell(P,(-EYE[0],EYE[1],EYE[2]),(ER,)*3))
def sdf_maw(P): return ell(P,(0,2.51,-0.46),(0.11,0.06,0.09))
def teeth(P,upper):
    ds=[]
    for a in np.radians(np.linspace(-62,62,10 if upper else 8)):
        b=V(0.1*math.sin(a),2.56 if upper else 2.44,-0.44-0.075*math.cos(a)); t=b+V(0,-0.05 if upper else 0.05,-0.004)
        ds.append(cap(P,b,t,0.007,0.0012))
    return union(ds,0.002)
def sdf_jaw(P):
    d=ell(P,(0,2.42,-0.42),(0.15,0.06,0.13))
    d=smin(d,path(P,[(0,2.42,-0.5),(0,2.36,-0.54),(0,2.3,-0.56)],[0.022,0.014,0.002],0.012),0.02)  # markaziy kalta tikan (iyak o'rtasida)
    return d
def sdf_uarm(P): return union([ell(P,tuple(SH+V(0.03,0.02,0)),(0.2,0.22,0.22)),cap(P,SH,EL,0.12,0.09),ell(P,tuple(SH+(EL-SH)*0.45),(0.11,0.22,0.11)),ell(P,tuple(EL+V(0.02,0.03,0.08)),(0.07,0.08,0.07))],0.06)
def sdf_larm(P): return union([cap(P,EL,WR,0.1,0.06),ell(P,tuple(EL+(WR-EL)*0.3),(0.11,0.16,0.11))],0.06)
DXS=(-0.13,-0.045,0.045,0.13); FING=[]
for i,dx in enumerate(DXS):
    ln=1.0-0.12*abs(i-1.5)/1.5
    K=WR+V(dx*0.75,-0.22,-0.06); J1=K+V(dx*0.45,-0.36*ln,-0.16); J2=J1+V(dx*0.3,-0.3*ln,0.05); T=J2+V(dx*0.2,-0.18*ln,0.2)  # 3 bo'g'im, ichkariga ilmoqdek bukilgan
    FING.append((K,J1,J2,T))
def sdf_hand(P):
    ds=[ell(P,tuple(WR+V(0,-0.1,-0.03)),(0.12,0.13,0.07))]
    for K,J1,J2,T in FING: ds+=[path(P,[K,J1,J2,T],[0.045,0.038,0.03,0.018],0.02)]+[ell(P,tuple(q),(0.056,0.056,0.056)) for q in (J1,J2)]
    return union(ds,0.03)
def sdf_claw(P): return union([path(P,[T,T+V(0,-0.05,0.08),T+V(0,-0.03,0.19)],[0.016,0.01,0.0015],0.01) for K,J1,J2,T in FING],0.01)
# iyakning ikki yonidagi ayirsimon S-shaklli tentakllar (bosh koordinatalarida, egilish keyin qo'llanadi)
TA=[V(0.12,2.45,-0.44),V(0.21,2.41,-0.52),V(0.29,2.35,-0.58),V(0.33,2.27,-0.62),V(0.32,2.19,-0.68),V(0.28,2.15,-0.74)]
TB=[V(0.10,2.42,-0.38),V(0.18,2.37,-0.43),V(0.24,2.3,-0.48),V(0.26,2.22,-0.5),V(0.24,2.14,-0.55),V(0.2,2.1,-0.6)]
def sdf_ta(P): return path(P,TA,[0.05,0.043,0.035,0.026,0.015,0.004],0.015)
def sdf_tb(P): return path(P,TB,[0.046,0.039,0.031,0.023,0.013,0.004],0.015)
def sdf_thigh(P): return union([cap(P,HIP,KNEE,0.19,0.13),ell(P,tuple(HIP+(KNEE-HIP)*0.4),(0.17,0.15,0.22))],0.06)
def sdf_shin(P): return union([cap(P,KNEE,ANK,0.11,0.065),ell(P,tuple(KNEE+(ANK-KNEE)*0.25+V(0,0,0.05)),(0.1,0.18,0.11)),ell(P,tuple(KNEE),(0.1,0.1,0.1))],0.05)
TDX=(-0.06,0.0,0.06)
def sdf_foot(P):
    ds=[cap(P,ANK,HEEL,0.06,0.07),ell(P,(0.5,0.06,0.5),(0.11,0.06,0.17))]
    for dx in TDX: ds.append(path(P,[V(0.5+dx,0.05,0.42),V(0.5+dx*1.4,0.05,0.28),V(0.5+dx*1.6,0.03,0.17)],[0.04,0.032,0.025],0.02))  # uzun, yerni changallagan barmoqlar
    return union(ds,0.04)
def sdf_toeclaw(P): return union([path(P,[V(0.5+dx*1.6,0.035,0.18),V(0.5+dx*1.65,0.025,0.11),V(0.5+dx*1.7,0.003,0.07)],[0.024,0.014,0.002],0.01) for dx in TDX],0.01)
def cloth_card(xt,xb,yt,yb,zs,nz,wrap,rows=6,cols=4):
    zt,zm,zb=zs; Vt=[];UV=[];F=[]
    for i in range(rows+1):
        f=i/rows; y=yt+(yb-yt)*f; zc=zt*(1-f)*(1-2*f)+zm*4*f*(1-f)+zb*f*(2*f-1); hw=xt+(xb-xt)*f
        for j in range(cols+1):
            x=-hw+2*hw*j/cols; Vt.append((x,y,zc+wrap*x*x+0.03*f*math.sin(x*14+f*3))); UV.append((j/cols,0.01+0.98*f))
    for i in range(rows):
        for j in range(cols): b=i*(cols+1)+j; F+=[(b,b+1,b+cols+2),(b,b+cols+2,b+cols+1)]
    return np.array(Vt),np.array(F),np.tile(V(0,0,nz),(len(Vt),1)),np.array(UV)
def mirror(v,f,n):
    v2=v.copy(); v2[:,0]*=-1; n2=n.copy(); n2[:,0]*=-1; return v2,f[:,[0,2,1]].copy(),n2
def uv_eye(v):
    L=v[:,0]>0; c=[v[L].mean(0),v[~L].mean(0)]; cx=np.where(L,c[0][0],c[1][0]); cy=np.where(L,c[0][1],c[1][1])
    return np.stack([0.5+(cx-v[:,0])/(2*ER),0.5+(cy-v[:,1])/(2*ER)],1)
SK=1/1.2
SPEC=[('torso','skin',sdf_torso,0.03,((-0.9,1.15,-0.45),(0.9,3.3,0.85)),0.04,SK,False),
 ('head','skin',sdf_head,0.018,((-0.4,2.4,-0.8),(0.4,3.46,0.45)),0.026,SK,False),
 ('head','eye',sdf_eyes,0.012,((-0.35,2.8,-0.72),(0.35,3.05,-0.45)),0.0,'eye',False),
 ('head','maw',sdf_maw,0.01,((-0.12,2.46,-0.56),(0.12,2.58,-0.37)),0.015,1/0.4,False),
 ('head','teeth',lambda P: teeth(P,True),0.004,((-0.11,2.49,-0.53),(0.11,2.56,-0.35)),0.006,1/0.3,False),
 ('jaw','skin',sdf_jaw,0.011,((-0.22,1.98,-0.58),(0.22,2.52,-0.27)),0.016,SK,False),
 ('jaw','teeth',lambda P: teeth(P,False),0.004,((-0.11,2.44,-0.53),(0.11,2.52,-0.35)),0.006,1/0.3,False),
 ('arm_upper','skin',sdf_uarm,0.02,((0.35,1.85,-0.3),(1.0,2.95,0.32)),0.028,SK,True),
 ('arm_lower','skin',sdf_larm,0.016,((0.65,1.15,-0.95),(1.0,2.15,0.1)),0.024,SK,True),
 ('hand','skin',sdf_hand,0.011,((0.55,0.08,-1.2),(1.25,1.35,-0.5)),0.016,SK,True),
 ('hand','claw',sdf_claw,0.007,((0.55,0.05,-1.05),(1.25,0.3,-0.4)),0.01,1/0.3,True),
 ('tendril_a','skin',sdf_ta,0.009,((0.04,2.05,-0.85),(0.42,2.52,-0.3)),0.012,SK,True),
 ('tendril_b','skin',sdf_tb,0.009,((0.04,2.02,-0.7),(0.35,2.48,-0.28)),0.012,SK,True),
 ('thigh','skin',sdf_thigh,0.018,((0.05,1.0,-0.5),(0.85,1.92,0.5)),0.026,SK,True),
 ('shin','skin',sdf_shin,0.014,((0.38,0.28,-0.45),(0.78,1.35,0.7)),0.02,SK,True),
 ('foot','skin',sdf_foot,0.013,((0.33,-0.03,0.1),(0.68,0.45,0.8)),0.018,SK,True),
 ('foot','claw',sdf_toeclaw,0.007,((0.33,-0.03,0.02),(0.68,0.1,0.25)),0.01,1/0.3,True)]
MATS=['skin','eye','maw','teeth','claw','cloth']
def build(res_root):
    LEAN={'torso','head','jaw','tendril_a','tendril_b'}; TILTED={'head','jaw','tendril_a','tendril_b'}
    raw=[(g,mat,mesh((lambda s,t: (lambda P: s(tilt(lean(P)) if t else lean(P))))(s,g in TILTED) if g in LEAN else s,h,((bb[0][0],bb[0][1]-0.15,bb[0][2]-0.4),(bb[1][0],bb[1][1],bb[1][2]+0.1)) if g in LEAN else bb),cell,uvm,mir) for g,mat,s,h,bb,cell,uvm,mir in SPEC]
    k=1.0
    while True:
        parts=[]
        for g,mat,(v,f,n),cell,uvm,mir in raw:
            if cell>0: v,f,n=decimate(v,f,n,cell*k)
            for sfx,vv,ff,nn in [('_l' if mir else '',v,f,n)]+([('_r',)+mirror(v,f,n)] if mir else []):
                uv,fvt=(uv_eye(vv),ff) if uvm=='eye' else uv_box(vv,ff,uvm)
                parts.append((g+sfx,mat,vv,ff,nn,uv,fvt))
        for g,args in (('cloth_f',(0.34,0.14,1.84,0.70,(0.0,-0.14,-0.2),-1.0,0.8)),('cloth_b',(0.40,0.16,1.84,1.05,(0.56,0.69,0.6),1.0,-0.8))):
            v,f,n,uv=cloth_card(*args); parts.append((g,'cloth',v,f,n,uv,f))
        tris=sum(len(p[3]) for p in parts)
        if tris<=59800: break
        k*=1.08
    os.makedirs(res_root+'models/entity',exist_ok=True); write_obj(res_root+'models/entity/black_monster.obj',parts,'black_monster.mtl')
    open(res_root+'models/entity/black_monster.mtl','w').write(''.join(f'newmtl {m}\nmap_Kd ../../textures/entity/black_monster/{m}.png\n\n' for m in MATS))
    return tris,k
def textures(td):
    os.makedirs(td,exist_ok=True); rng=np.random.default_rng(21); N=1024
    # ho'l, silliq, charmsimon teri: #232A30 asos, #0E1114 burmalar, #4E5D67 yaltiroq joylar, tomirlar
    img=np.array((30,36,42))+pnoise(N,N,2.6)[...,None]*5+pnoise(N,N,1.5)[...,None]*2
    cr=np.exp(-(pnoise(N,N,1.6,ax=4.0)/0.06)**2)[...,None]; img=img*(1-0.55*cr)+np.array((14,17,20))*0.55*cr
    vl=Image.new('L',(N,N),0); dv=ImageDraw.Draw(vl)
    for _ in range(40):
        x,y=rng.uniform(0,N,2); a=rng.uniform(0,6.283); pts=[(x,y)]
        for k in range(int(rng.integers(6,14))):
            a+=rng.normal(0,0.35); x+=math.cos(a)*rng.uniform(15,35); y+=math.sin(a)*rng.uniform(15,35); pts.append((x,y))
        dv.line(pts,fill=int(rng.uniform(90,170)),width=int(rng.integers(2,5)))
    va=np.array(vl.filter(ImageFilter.GaussianBlur(1.5)))[...,None]/255.0*0.55; img=img*(1-va)+np.array((20,26,33))*va
    sh=np.array(Image.fromarray((np.clip(pnoise(N,N,2.8)-0.6,0,1.2)/1.2*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6)))[...,None]/255.0
    img=img*(1-0.8*sh)+np.array((78,93,103))*0.8*sh
    pores=rng.random((N,N))<0.006; img[pores]*=0.7
    Image.fromarray(np.clip(img,0,255).astype(np.uint8)).save(td+'/skin.png')
    S=256; yy,xx=np.mgrid[0:S,0:S]; r=np.hypot(xx-128,yy-128)
    e=np.array((222,221,212))*(1-0.18*np.clip((r-60)/68,0,1))[...,None]+pnoise(S,S,2.0)[...,None]*3
    im=Image.fromarray(np.clip(e,0,255).astype(np.uint8)); d=ImageDraw.Draw(im)
    for _ in range(10):
        a=rng.uniform(0,6.283); x,y=128+127*math.cos(a),128+127*math.sin(a); pts=[(x,y)]
        for k in range(5):
            a2=a+math.pi+rng.normal(0,0.5); x+=math.cos(a2)*rng.uniform(5,10); y+=math.sin(a2)*rng.uniform(5,10); pts.append((x,y))
        d.line(pts,fill=(178,170,168),width=1)
    d.ellipse((110,166,146,202),fill=(20,22,24)); d.ellipse((115,170,122,177),fill=(250,250,248))  # qorachiq ko'zning qosh ostidan ko'rinadigan qismida
    im.filter(ImageFilter.GaussianBlur(0.5)).save(td+'/eye.png')
    T=128
    Image.fromarray(np.clip(np.array((205,203,190))+pnoise(T,T,1.8)[...,None]*8,0,255).astype(np.uint8)).save(td+'/teeth.png')
    Image.fromarray(np.clip(np.array((30,10,12))+pnoise(T,T,1.8)[...,None]*5,0,255).astype(np.uint8)).save(td+'/maw.png')
    Image.fromarray(np.clip(np.array((33,39,45))+pnoise(S,S,1.5,ax=4)[...,None]*7,0,255).astype(np.uint8)).save(td+'/claw.png')
    # eski tolali latta #94875C, dog'lar #5C5336, pastki 45% titilgan bo'laklar va teshiklar
    C=512; yy,xx=np.mgrid[0:C,0:C]
    c=np.array((148,135,92))+pnoise(C,C,2.0)[...,None]*8+pnoise(C,C,1.4,ax=0.15)[...,None]*7
    st=np.clip(pnoise(C,C,2.3)-0.3,0,1.2)[...,None]/1.2; c=c*(1-0.8*st)+np.array((92,83,54))*0.8*st
    c=c*(1-0.3*(yy/C)[...,None]**1.5)
    a=np.ones((C,C),bool); x=0
    while x<C:
        w=int(rng.integers(5,40)); end=int(rng.integers(230,510)); kk=rng.uniform(0.5,4.0)
        for j in range(w):
            if x+j<C: a[max(0,end-int(abs(j-w/2)*kk)):,x+j]=False
        if x<C: a[max(0,end-int(rng.integers(60,200))):,x:x+2]=False
        x+=w
    for _ in range(14):
        hx,hy,hr=rng.integers(30,C-30),rng.integers(40,330),rng.integers(5,15); hole=(xx-hx)**2+((yy-hy)*1.4)**2<hr*hr
        rim=((xx-hx)**2+((yy-hy)*1.4)**2<(hr+5)**2)&~hole; c[rim]*=0.7; a[hole]=False
    out=np.concatenate([np.clip(c,0,255),np.where(a,255,0)[...,None]],2).astype(np.uint8)
    Image.fromarray(out,'RGBA').save(td+'/cloth.png')
if __name__=='__main__':
    R='src/main/resources/assets/blackmonster/'; textures(R+'textures/entity/black_monster'); print('tris',build(R))
