"""Teksturali preview: OBJ + PNG teksturalardan oddiy z-buffer rasterizator (alfa-test bilan). python tools/render_preview.py"""
import numpy as np, math, os
from PIL import Image
RES='src/main/resources/assets/blackmonster/'
def load():
    V=[];T=[];N=[];F={}
    mat='fur'
    for l in open(RES+'models/entity/black_monster.obj'):
        if l.startswith('v '): V.append(l.split()[1:4])
        elif l.startswith('vt '): T.append(l.split()[1:3])
        elif l.startswith('vn '): N.append(l.split()[1:4])
        elif l.startswith('usemtl'): mat=l.split()[1]
        elif l.startswith('f '): F.setdefault(mat,[]).append([int(x)-1 for c in l.split()[1:4] for x in c.split('/')])
    return np.array(V,float),np.array(T,float),np.array(N,float),{m:np.array(f) for m,f in F.items()}
def render(out,yaw,V,T,N,F,S=440,W=1440,H=1720):
    c,s=math.cos(math.radians(yaw)),math.sin(math.radians(yaw))
    rot=lambda A:np.stack([A[:,0]*c+A[:,2]*s,A[:,1],-A[:,0]*s+A[:,2]*c],1)
    Vr=rot(V); Nr=rot(N); sx=W/2-Vr[:,0]*S; sy=H-70-Vr[:,1]*S; z=Vr[:,2]
    L=np.array((-0.35,0.6,-0.72)); L/=np.linalg.norm(L)
    g=np.linspace(0,1,H)[:,None,None]; img=np.ones((H,W,3))*(232-30*g); zb=np.full((H,W),1e9)
    yy,xx=np.mgrid[0:H,0:W]; sh=((xx-W/2)/(1.25*S))**2+((yy-(H-70))/(0.22*S))**2<1; img[sh]*=0.72
    for mat,f in F.items():
        tex=np.array(Image.open(RES+f'textures/entity/black_monster/{mat}.png').convert('RGBA')).astype(float); th,tw=tex.shape[:2]
        bright=mat=='eye'
        for row in f:
            vi=row[0::3]; ti=row[1::3]; ni=row[2::3]
            X=sx[vi]; Y=sy[vi]; Z=z[vi]
            x0,x1=int(max(0,X.min())),int(min(W-1,X.max())); y0,y1=int(max(0,Y.min())),int(min(H-1,Y.max()))
            if x1<x0 or y1<y0: continue
            den=(Y[1]-Y[2])*(X[0]-X[2])+(X[2]-X[1])*(Y[0]-Y[2])
            if abs(den)<1e-9: continue
            px=xx[y0:y1+1,x0:x1+1]+0.5; py=yy[y0:y1+1,x0:x1+1]+0.5
            w0=((Y[1]-Y[2])*(px-X[2])+(X[2]-X[1])*(py-Y[2]))/den; w1=((Y[2]-Y[0])*(px-X[2])+(X[0]-X[2])*(py-Y[2]))/den; w2=1-w0-w1
            m=(w0>=-1e-4)&(w1>=-1e-4)&(w2>=-1e-4)
            if not m.any(): continue
            zz=w0*Z[0]+w1*Z[1]+w2*Z[2]; zsub=zb[y0:y1+1,x0:x1+1]; m&=zz<zsub
            if not m.any(): continue
            uv=T[ti]; u=(w0*uv[0,0]+w1*uv[1,0]+w2*uv[2,0])[m]; v=(w0*uv[0,1]+w1*uv[1,1]+w2*uv[2,1])[m]
            tu=((u%1.0)*(tw-1)).astype(int); tv=(((1-v)%1.0)*(th-1)).astype(int); col=tex[tv,tu]
            keep=col[:,3]>=128
            if not keep.any(): continue
            nn=Nr[ni]; n=np.outer(w0[m],nn[0])+np.outer(w1[m],nn[1])+np.outer(w2[m],nn[2]); n/=np.linalg.norm(n,axis=1,keepdims=True)+1e-9
            shade=np.ones(len(n)) if bright else 0.4+0.75*np.abs(n@L)
            ys,xs=np.nonzero(m); ys=ys[keep]+y0; xs=xs[keep]+x0
            zb[ys,xs]=zz[m][keep]; img[ys,xs]=np.clip(col[keep,:3]*shade[keep,None],0,255)
    Image.fromarray(img.astype(np.uint8)).resize((W//2,H//2),Image.LANCZOS).save(out)
if __name__=='__main__':
    V,T,N,F=load(); os.makedirs('previews',exist_ok=True)
    for nm,yaw in (('front',0),('three_quarter',35),('side',90)): render(f'previews/preview_{nm}.png',yaw,V,T,N,F)
