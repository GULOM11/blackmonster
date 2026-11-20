"""Mesh/texture toolkit for Minecraft OBJ boss mods. Requires numpy, scipy, scikit-image, pillow, matplotlib."""
import numpy as np, scipy.sparse as sp, math
from skimage.measure import marching_cubes

def ell(P, c, r):
    q = (P - np.array(c)) / np.array(r); k0 = np.linalg.norm(q, axis=1); k1 = np.linalg.norm(q / np.array(r), axis=1) + 1e-9
    return k0 * (k0 - 1) / k1
def cap(P, a, b, ra, rb):
    a = np.array(a, float); b = np.array(b, float); pa = P - a; ba = b - a
    h = np.clip(pa @ ba / (ba @ ba), 0, 1); return np.linalg.norm(pa - np.outer(h, ba), axis=1) - (ra + (rb - ra) * h)
def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0, 1); return b * (1 - h) + a * h - k * h * (1 - h)
def union(ds, k):
    d = ds[0]
    for e in ds[1:]: d = smin(d, e, k)
    return d
def path(P, pts, rs, k=0.05):
    return union([cap(P, pts[i], pts[i + 1], rs[i], rs[i + 1]) for i in range(len(pts) - 1)], k)

def _adj(f, n):
    I = np.concatenate([f[:, 0], f[:, 1], f[:, 2], f[:, 1], f[:, 2], f[:, 0]]); J = np.concatenate([f[:, 1], f[:, 2], f[:, 0], f[:, 0], f[:, 1], f[:, 2]])
    A = sp.coo_matrix((np.ones(len(I)), (I, J)), shape=(n, n)).tocsr(); A.data[:] = 1; return A, np.asarray(A.sum(1)).ravel() + 1e-9
def taubin(v, f, it=4):
    A, deg = _adj(f, len(v))
    for lam in [0.5, -0.53] * it: v = v + lam * ((A @ v) / deg[:, None] - v)
    return v

def mesh(sdf, h, bbox):
    """sdf(P[N,3]) -> distances; h voxel size (blocks); bbox ((x0,y0,z0),(x1,y1,z1)). Returns verts, faces, normals."""
    lo = np.array(bbox[0], float) - 2 * h; hi = np.array(bbox[1], float) + 2 * h
    ax = [np.arange(lo[i], hi[i] + h, h) for i in range(3)]
    G = np.stack(np.meshgrid(*ax, indexing='ij'), -1).reshape(-1, 3)
    v, f, _, _ = marching_cubes(sdf(G).reshape([len(a) for a in ax]), 0.0, spacing=(h, h, h)); v += lo
    v = taubin(v, f)
    e = 1e-3; g = np.stack([sdf(v + np.eye(3)[k] * e) - sdf(v - np.eye(3)[k] * e) for k in range(3)], 1)
    n = g / (np.linalg.norm(g, axis=1, keepdims=True) + 1e-9)
    return orient(v, f, n)
def orient(v, f, n):
    fn = np.cross(v[f[:, 1]] - v[f[:, 0]], v[f[:, 2]] - v[f[:, 0]]); flip = (fn * n[f].mean(1)).sum(1) < 0
    f = f.copy(); f[flip] = f[flip][:, [0, 2, 1]]; return v, f, n

def decimate(v, f, n, cell):
    """Vertex clustering. cell = float or (cx,cy,cz) for thin parts."""
    c = np.array(cell if isinstance(cell, tuple) else (cell,) * 3); inv = np.unique(np.floor(v / c).astype(np.int64), axis=0, return_inverse=True)[1].ravel()
    m = inv.max() + 1; cnt = np.bincount(inv, minlength=m)[:, None]
    nv = np.stack([np.bincount(inv, v[:, k], m) for k in range(3)], 1) / cnt
    nn = np.stack([np.bincount(inv, n[:, k], m) for k in range(3)], 1); nn /= np.linalg.norm(nn, axis=1, keepdims=True) + 1e-9
    nf = inv[f]; nf = nf[(nf[:, 0] != nf[:, 1]) & (nf[:, 1] != nf[:, 2]) & (nf[:, 0] != nf[:, 2])]
    nf = nf[np.sort(np.unique(np.sort(nf, 1), axis=0, return_index=True)[1])]
    used = np.unique(nf); remap = -np.ones(m, int); remap[used] = np.arange(len(used))
    nv, nn, nf = nv[used], nn[used], remap[nf]
    return orient(taubin(nv, nf, 2), nf, nn)

def uv_box(v, f, scale=1 / 1.5):
    """Per-face box projection for tileable skin. Returns uv array and per-face uv indices."""
    ax = np.argmax(np.abs(np.cross(v[f[:, 1]] - v[f[:, 0]], v[f[:, 2]] - v[f[:, 0]])), 1)
    keys = {}; uv = []; fvt = np.zeros_like(f)
    for i in range(len(f)):
        for j in range(3):
            k = (f[i, j], ax[i])
            if k not in keys:
                p = v[f[i, j]]; q = (p[2], p[1]) if ax[i] == 0 else ((p[0], p[2]) if ax[i] == 1 else (p[0], p[1]))
                keys[k] = len(uv); uv.append((q[0] * scale, -q[1] * scale))
            fvt[i, j] = keys[k]
    return np.array(uv), fvt
def uv_planar(v):
    x = np.abs(v[:, 0]); y = v[:, 1]
    return np.stack([(x - x.min()) / (x.max() - x.min()), (y.max() - y) / (y.max() - y.min())], 1)

def write_obj(path_, parts, mtllib='model.mtl'):
    """parts: list of (group, material, v, f, n, uv, fvt)."""
    V, T, N, L = [], [], [], []; vb = tb = 0
    for g, mat, v, f, n, uv, fvt in parts:
        L += [f'o {g}', f'usemtl {mat}']
        V += [f'v {p[0]:.4f} {p[1]:.4f} {p[2]:.4f}' for p in v]; N += [f'vn {p[0]:.3f} {p[1]:.3f} {p[2]:.3f}' for p in n]
        T += [f'vt {p[0]:.4f} {1 - p[1]:.4f}' for p in uv]
        for i in range(len(f)):
            L.append('f ' + ' '.join(f'{f[i, j] + vb + 1}/{fvt[i, j] + tb + 1}/{f[i, j] + vb + 1}' for j in range(3)))
        vb += len(v); tb += len(uv)
    open(path_, 'w').write('\n'.join([f'mtllib {mtllib}'] + V + T + N + L) + '\n')

def pnoise(H, W, beta, ax=1.0, rng=np.random.default_rng(0)):
    fy = np.fft.fftfreq(H)[:, None]; fx = np.fft.fftfreq(W)[None, :] * ax; fr = np.sqrt(fx ** 2 + fy ** 2); fr[0, 0] = 1
    F = (rng.normal(size=(H, W)) + 1j * rng.normal(size=(H, W))) / fr ** beta; F[0, 0] = 0
    n = np.real(np.fft.ifft2(F)); return (n - n.mean()) / n.std()
def skin_tile(N, mid, dark, hi):
    """Tileable wrinkled skin. mid/dark/hi = RGB tuples from the reference image."""
    mid, dark, hi = map(np.array, (mid, dark, hi))
    img = np.ones((N, N, 3)) * mid + pnoise(N, N, 2.2)[..., None] * 14 + pnoise(N, N, 1.3)[..., None] * 6
    c = np.exp(-(pnoise(N, N, 1.6, ax=5.0) / 0.07) ** 2)[..., None]; s = np.roll(c, -3, axis=0)
    img = img * (1 - 0.75 * c) + dark * 0.75 * c; img = img * (1 - 0.35 * s) + hi * 0.35 * s
    return np.clip(img, 0, 255).astype(np.uint8)

def preview(parts_colored, out, azim=-125):
    """parts_colored: list of (v, f, n, rgb01). Saves a shaded render."""
    import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
    from mpl_toolkits.mplot3d.art3d import Poly3DCollection
    L = np.array([-0.4, 0.7, -0.6]); L /= np.linalg.norm(L); polys = []; cols = []
    for v, f, n, rgb in parts_colored:
        lam = 0.35 + 0.65 * np.clip(n[f].mean(1) @ L, 0, 1); polys.append(v[f][:, :, [0, 2, 1]]); cols.append(np.clip(np.outer(lam, rgb), 0, 1))
    fig = plt.figure(figsize=(8, 7)); a = fig.add_subplot(111, projection='3d')
    a.add_collection3d(Poly3DCollection(np.concatenate(polys), facecolors=np.concatenate(cols), edgecolors='none'))
    a.set_xlim(-2.6, 2.6); a.set_ylim(-3.4, 1.8); a.set_zlim(0, 4.2); a.set_box_aspect((5.2, 5.2, 4.2)); a.view_init(12, azim); a.set_axis_off()
    plt.savefig(out, dpi=120, bbox_inches='tight'); plt.close()
