package com.blackmonster.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Minimal OBJ loader: groups (o/g) = animated parts, usemtl = texture. Silliq normallar (vn) saqlanadi. */
public final class ObjMesh {
    /** material -> part -> triangles (har vertex: x y z u v nx ny nz) */
    public final Map<String, Map<String, float[]>> data = new LinkedHashMap<>();
    private static final Pattern WS = Pattern.compile("\\s+");

    private static final class FloatList {
        float[] a = new float[4096]; int n;
        void add(float v) { if (n == a.length) a = Arrays.copyOf(a, n * 2); a[n++] = v; }
        float get(int i) { return a[i]; }
    }

    public static ObjMesh load(ResourceManager rm, ResourceLocation loc) throws IOException {
        FloatList pos = new FloatList(), uv = new FloatList(), nrm = new FloatList();
        Map<String, Map<String, FloatList>> acc = new LinkedHashMap<>();
        String part = "default", mat = "skin";
        try (BufferedReader r = rm.openAsReader(loc)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] s = WS.split(line);
                switch (s[0]) {
                    case "o", "g" -> part = s.length > 1 ? s[1] : "default";
                    case "usemtl" -> mat = s[1];
                    case "v" -> { pos.add(Float.parseFloat(s[1])); pos.add(Float.parseFloat(s[2])); pos.add(Float.parseFloat(s[3])); }
                    case "vt" -> { uv.add(Float.parseFloat(s[1])); uv.add(1.0F - Float.parseFloat(s[2])); }
                    case "vn" -> { nrm.add(Float.parseFloat(s[1])); nrm.add(Float.parseFloat(s[2])); nrm.add(Float.parseFloat(s[3])); }
                    case "f" -> {
                        FloatList out = acc.computeIfAbsent(mat, k -> new LinkedHashMap<>()).computeIfAbsent(part, k -> new FloatList());
                        int n = s.length - 1;
                        for (int k = 1; k <= n - 2; k++) {
                            corner(out, s[1], pos, uv, nrm); corner(out, s[k + 1], pos, uv, nrm); corner(out, s[k + 2], pos, uv, nrm);
                        }
                    }
                    default -> { }
                }
            }
        }
        ObjMesh mesh = new ObjMesh();
        acc.forEach((m, parts) -> {
            Map<String, float[]> pm = new LinkedHashMap<>();
            parts.forEach((p, fl) -> pm.put(p, Arrays.copyOf(fl.a, fl.n)));
            mesh.data.put(m, pm);
        });
        return mesh;
    }

    private static void corner(FloatList out, String tok, FloatList pos, FloatList uv, FloatList nrm) {
        String[] t = tok.split("/");
        int vi = Integer.parseInt(t[0]) - 1;
        int ti = t.length > 1 && !t[1].isEmpty() ? Integer.parseInt(t[1]) - 1 : -1;
        int ni = t.length > 2 && !t[2].isEmpty() ? Integer.parseInt(t[2]) - 1 : -1;
        out.add(pos.get(vi * 3)); out.add(pos.get(vi * 3 + 1)); out.add(pos.get(vi * 3 + 2));
        out.add(ti >= 0 ? uv.get(ti * 2) : 0F); out.add(ti >= 0 ? uv.get(ti * 2 + 1) : 0F);
        out.add(ni >= 0 ? nrm.get(ni * 3) : 0F); out.add(ni >= 0 ? nrm.get(ni * 3 + 1) : 1F); out.add(ni >= 0 ? nrm.get(ni * 3 + 2) : 0F);
    }
}
