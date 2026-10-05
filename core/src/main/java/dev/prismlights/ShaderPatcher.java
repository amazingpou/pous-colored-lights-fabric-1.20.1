package dev.prismlights;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Modify light evaluation in a material program, never the final screen color.
 * Unknown programs are deliberately untouched; there is no overlay fallback. */
public final class ShaderPatcher {
    private static final String COMMON = """
        uniform sampler3D prismIrradiance;
        uniform sampler3D prismSkyExposure;
        uniform float prismSkyActive;
        uniform vec3 prismGridMinRel;
        uniform vec3 prismGridSize;
        uniform float prismActive;
        uniform float prismDaylight;
        uniform float prismStaticActive;
        uniform float prismStrength;
        uniform float prismArtistic;
        uniform sampler3D prismMobileOcclusion;
        uniform int prismMobileCount;
        uniform vec4 prismMobilePositions[PRISM_MOBILE_LIMIT];
        uniform vec4 prismMobileColors[PRISM_MOBILE_LIMIT];
        uniform vec4 prismMobileHues[PRISM_MOBILE_LIMIT];
        uniform vec4 prismMobileOrigins[PRISM_MOBILE_LIMIT];
        uniform mat4 prismInverseView;
        uniform mat4 prismInverseProjection;
        vec3 PrismEncodeHue(vec3 rgb) {
            float peak=max(max(rgb.r,rgb.g),rgb.b), low=min(min(rgb.r,rgb.g),rgb.b);
            float delta=peak-low;
            float hue=0.0;
            if(delta>0.00001) {
                if(peak==rgb.r) hue=mod((rgb.g-rgb.b)/delta,6.0);
                else if(peak==rgb.g) hue=(rgb.b-rgb.r)/delta+2.0;
                else hue=(rgb.r-rgb.g)/delta+4.0;
            }
            float angle=hue*1.0471975512;
            float saturation=peak>0.0?delta/peak:0.0;
            return vec3(cos(angle)*saturation*saturation,sin(angle)*saturation*saturation,peak);
        }
        vec3 PrismDecodeHue(vec3 encoded) {
            float hue=atan(encoded.y,encoded.x)/6.2831853072;
            float saturation=clamp(sqrt(length(encoded.xy)),0.0,1.0);
            vec3 rgb=clamp(abs(fract(hue+vec3(0.0,2.0/3.0,1.0/3.0))*6.0-3.0)-1.0,0.0,1.0);
            return max(encoded.z,0.0)*mix(vec3(1.0),rgb,saturation);
        }
        vec4 PrismIncidentLight(vec3 cameraRelativePosition) {
            if (prismActive < 0.5) return vec4(0.0);
            vec4 stationary=vec4(0.0);
            if(prismStaticActive>0.5) {
                vec3 uv=(cameraRelativePosition-prismGridMinRel)/prismGridSize;
                if(all(greaterThanEqual(uv,vec3(0.0))) && all(lessThanEqual(uv,vec3(1.0))))
                    stationary=texture(prismIrradiance,uv);
            }
            if(prismMobileCount==0) return stationary;
            float energy=-log(max(1.0-stationary.a,0.0001))/3.5;
            vec3 palette=pow(max(stationary.rgb,vec3(0.0))/max(max(stationary.r,stationary.g),max(stationary.b,0.00001)),vec3(1.0/2.2));
            vec3 rgbSum=palette*energy,hueSum=PrismEncodeHue(palette)*energy;
            float movingEnergy=0.0;
            for(int i=0;i<PRISM_MOBILE_LIMIT;i++) {
                if(i>=prismMobileCount) break;
                float falloff=max(0.0,1.0-length(cameraRelativePosition-prismMobilePositions[i].xyz)/prismMobilePositions[i].w);
                if(falloff<=0.0) continue;
                vec3 local=(cameraRelativePosition-prismMobileOrigins[i].xyz)/27.0;
                if(any(lessThan(local,vec3(0.0))) || any(greaterThan(local,vec3(1.0)))) continue;
                float visible=texture(prismMobileOcclusion,vec3((prismMobileOrigins[i].w+local.x)/PRISM_MOBILE_LIMIT.0,local.yz)).r;
                float weight=falloff*falloff*prismMobileColors[i].w*visible;
                rgbSum+=prismMobileColors[i].rgb*weight;
                hueSum+=prismMobileHues[i].xyz*weight;
                movingEnergy+=weight;
            }
            // With no mobile contribution keep the established static result exactly.
            if(movingEnergy<0.00001) return stationary;
            energy+=movingEnergy;
            vec3 mixed=prismArtistic>0.5?PrismDecodeHue(hueSum/energy):rgbSum/energy;
            mixed/=max(max(mixed.r,mixed.g),max(mixed.b,0.00001));
            float coverage=1.0-exp(-3.5*energy);
            return vec4(pow(max(mixed,vec3(0.0)),vec3(2.2))*coverage*prismStrength*1.6,coverage);
        }
        vec4 PrismSurfaceLight(vec3 position, vec3 worldNormal) {
            // The field lives in air, not inside opaque cubes. Sample the
            // outward half-voxel so a floor does not blend with its unlit
            // interior. Geometry sampling must not move with the sun angle.
            return PrismIncidentLight(position + worldNormal * 0.5);
        }
        float PrismSunExposure(float skyLight) {
            // Bright mornings are daylight, not a blend with the night curve.
            // Local skylight keeps enclosed rooms colorful even at noon.
            float sun = smoothstep(0.0, 0.35, prismDaylight);
            // Smooth lighting beside opaque lamps can report slightly less
            // than full sky. Keep those outdoor vertices on the day curve.
            return sun * smoothstep(0.10, 0.85, clamp(skyLight, 0.0, 1.0));
        }
        float PrismVertexSunExposure(float skyLight) {
            // Core/Sodium interpolate skylight from neighboring blocks. An
            // opaque lamp lowers those vertex values even under open sky.
            // Compensate that average separately from pack fragment lighting.
            return PrismSunExposure(clamp(skyLight * (0.85 / 0.70), 0.0, 1.0));
        }
        float PrismLocalSunExposure(vec3 position, float fallbackExposure) {
            if(prismSkyActive<0.5) return fallbackExposure;
            vec3 grid = position - prismGridMinRel;
            if(any(lessThan(grid,vec3(0.0))) || any(greaterThanEqual(grid,prismGridSize))) return fallbackExposure;
            float sky = texture(prismSkyExposure,(grid+vec3(0.5))/prismGridSize).r;
            // Local air skylight distinguishes foliage/roofs from vertex AO.
            // Open sky stays exactly on the established subtle daylight curve.
            return smoothstep(0.0,0.35,prismDaylight) * pow(clamp(sky,0.0,1.0),3.0);
        }
        vec3 PrismDisplayRadiance(vec3 linearRgb) {
            // Scale the entire spectrum when the peak exceeds display range.
            // Clipping channels independently changes blue/purple hue near
            // a lamp or when many sources overlap.
            float peak = max(max(linearRgb.r, linearRgb.g), linearRgb.b);
            return pow(max(linearRgb, vec3(0.0)) / max(1.0, peak), vec3(1.0 / 2.2));
        }
        float PrismCoverage(float coverage, float exposure) {
            coverage = clamp(coverage, 0.0, 1.0);
            // At night residual warm vanilla light must not bleach the hue.
            // This smooth curve still goes to zero at the light's boundary.
            float night = 1.0 - pow(1.0 - coverage, 3.0);
            return mix(night, coverage * 0.12, exposure);
        }
        vec3 PrismBlockLighting(vec3 vanillaLighting, vec4 incident, float exposure) {
            float coverage = clamp(incident.a, 0.0, 1.0);
            vec3 radiance = incident.rgb * 2.0;
            // RGB already contains spatial falloff. Do not apply alpha a
            // second time at night; alpha replaces the neutral block light.
            vec3 night = vanillaLighting * (1.0 - PrismCoverage(coverage, 0.0)) + radiance;
            vec3 day = mix(vanillaLighting, radiance, coverage * 0.12);
            return mix(night, day, exposure);
        }
        vec3 PrismAmbientLighting(vec3 ambient, vec4 incident, float exposure) {
            // Match the artistic night lightmap: neutral moon/minimum light
            // must not wash a blue pool grey. Emission and night vision keep
            // their own pack terms; daylight and unlit areas are unaffected.
            vec3 hue = incident.rgb / max(max(incident.r, incident.g), max(incident.b, 0.001));
            float influence = PrismCoverage(incident.a, 0.0) * (1.0 - exposure);
            return mix(ambient, ambient * hue, influence);
        }
        vec3 PrismLightReadyAlbedo(vec3 albedo, vec3 incidentColor, float coverage, float exposure) {
            // Biome tint is extremely saturated on grass and foliage. A pure
            // component-wise multiply absorbs red/blue prism light almost
            // completely, even though the irradiance reached the surface.
            // Preserve texture luminance and most of the material hue while
            // opening enough spectral response for colored light to be seen.
            float luminance = dot(albedo, vec3(0.2126, 0.7152, 0.0722));
            float adaptation = clamp(coverage * mix(1.20, 0.12, exposure), 0.0, 1.20);
            vec3 hue = incidentColor / max(max(incidentColor.r, incidentColor.g), max(incidentColor.b, 0.001));
            // Open only the channels receiving colored light. Subtracting the
            // biome green makes daylight grass appear grey or dark.
            return albedo + max(vec3(luminance) - albedo, vec3(0.0)) * hue * adaptation;
        }
        vec3 PrismPositionFromClip(vec4 clip) {
            vec4 view = prismInverseProjection * clip;
            view /= view.w;
            return (prismInverseView * view).xyz;
        }
        vec4 PrismLightmapResolved(vec4 vanillaLight, vec4 incident, float exposure) {
            float coverage = clamp(incident.a, 0.0, 1.0);
            vec3 radiance = PrismDisplayRadiance(incident.rgb);
            // At night the lamp dominates the local lightmap color. In daylight
            // add only the chromatic excess into the remaining RGB headroom.
            // No daylight channel is reduced, including on tinted grass.
            // RGB already contains coverage; divide it out for the daylight
            // hue because the final blend below applies coverage once more.
            vec3 dayRadiance = PrismDisplayRadiance(incident.rgb / max(coverage, 0.001));
            float neutral = dot(dayRadiance, vec3(0.2126, 0.7152, 0.0722));
            vec3 chroma = max(dayRadiance - vec3(neutral), vec3(0.0));
            vec3 dayColor = vanillaLight.rgb + (vec3(1.0) - vanillaLight.rgb) * chroma * coverage * 0.12;
            vec3 nightColor = mix(vanillaLight.rgb, radiance, PrismCoverage(coverage, 0.0));
            return vec4(mix(nightColor, dayColor, exposure), vanillaLight.a);
        }
        vec4 PrismLightmap(vec4 vanillaLight, vec4 clip, float skyLight) {
            vec3 position = PrismPositionFromClip(clip);
            vec4 incident = PrismIncidentLight(position);
            float exposure = PrismLocalSunExposure(position,PrismVertexSunExposure(skyLight));
            return PrismLightmapResolved(vanillaLight,incident,exposure);
        }
        vec4 PrismLitVertex(vec4 tint, vec4 vanillaLight, vec4 clip, float skyLight) {
            vec3 position = PrismPositionFromClip(clip);
            vec4 incident = PrismIncidentLight(position);
            float coverage = clamp(incident.a, 0.0, 1.0);
            float exposure = PrismLocalSunExposure(position,PrismVertexSunExposure(skyLight));
            vec4 lit = PrismLightmapResolved(vanillaLight, incident, exposure);
            return vec4(PrismLightReadyAlbedo(tint.rgb, incident.rgb, coverage, exposure) * lit.rgb, tint.a * lit.a);
        }
        """.replace("PRISM_MOBILE_LIMIT",Integer.toString(LightSourceLimit.MOBILE_MAX));
    private static final Pattern MAIN=Pattern.compile("\\bvoid\\s+main\\s*\\(");
    private static final Pattern CLIP_ASSIGNMENT=Pattern.compile("\\bgl_Position\\s*=");
    private static final Pattern BSL_LIGHT=Pattern.compile("vec3\\s+blockLighting\\s*=\\s*blocklightCol\\s*\\*[^;]+;");
    private static final Pattern CORE_LIGHT=Pattern.compile("minecraft_sample_lightmap\\s*\\(\\s*Sampler2\\s*,\\s*UV2\\s*\\)|texelFetch\\s*\\(\\s*Sampler2\\s*,\\s*UV2\\s*/\\s*16\\s*,\\s*0\\s*\\)");
    private static final Pattern CORE_TINTED_LIGHT=Pattern.compile("\\bColor\\s*\\*\\s*(minecraft_sample_lightmap\\s*\\(\\s*Sampler2\\s*,\\s*UV2\\s*\\))");
    private static final Pattern SODIUM_TINTED_LIGHT=Pattern.compile("\\b_vert_color\\s*\\*\\s*(texture\\s*\\(\\s*u_LightTex\\s*,\\s*_vert_tex_light_coord\\s*\\))");
    private static final Pattern COMPLEMENTARY_LIGHT=Pattern.compile("vec3\\s+finalDiffuse\\s*=\\s*[^;]*\\bblockLighting\\b[^;]*\\bsceneLighting\\b[^;]*\\bminLighting\\b[^;]*;");
    private static final Pattern BSL_ALBEDO=Pattern.compile("\\balbedo\\s*\\*=\\s*max\\(\\s*sceneLighting\\s*\\+\\s*blockLighting[^;]*\\bminLighting\\b[^;]*;");
    public static String patch(String source) {
        if(source.contains("uniform sampler3D prismIrradiance")) return source;
        Matcher complementary=COMPLEMENTARY_LIGHT.matcher(source);
        if(packLighting(source,"DoLighting","playerPos",complementary)
            && parameters(source,"DoLighting","inout\\s+vec4\\s+color", "vec3\\s+worldGeoNormal", "float\\s+emission", "vec2\\s+lightmap")) {
            // After the pack's voxel/held lighting, before diffuse material
            // shading. playerPos is camera-relative in both r5 styles.
            // Iris may already have consumed pack preprocessor defines here.
            // Use the verified normal parameter for every material instead of
            // adding a terrain #ifdef that would silently disable the bias.
            String replacement="vec4 prismIncident = PrismSurfaceLight(playerPos, normalize(worldGeoNormal));\n"
                +"float prismExposure = PrismLocalSunExposure(playerPos + normalize(worldGeoNormal) * 0.5, PrismSunExposure(lightmap.y));\n"
                +"blockLighting = PrismBlockLighting(blockLighting, prismIncident, prismExposure);\n"
                +"sceneLighting = PrismAmbientLighting(sceneLighting, prismIncident, prismExposure);\n"
                +"minLighting = PrismAmbientLighting(minLighting, prismIncident, prismExposure);\n"
                +"color.rgb = PrismLightReadyAlbedo(color.rgb, prismIncident.rgb, clamp(prismIncident.a, 0.0, 1.0) * (1.0 - prismExposure) * (1.0 - clamp(emission, 0.0, 1.0)), prismExposure);\n"
                +complementary.group();
            String result=source.substring(0,complementary.start())+replacement+source.substring(complementary.end());
            int function=functionStart(result,"DoLighting");
            return result.substring(0,function)+COMMON+"\n"+result.substring(function);
        }
        // This function is shared by BSL terrain, entities, hand, water and block
        // entities. Adapt incident light and material response before TAA.
        Matcher bsl=BSL_LIGHT.matcher(source);
        Matcher bslAlbedo=BSL_ALBEDO.matcher(source);
        if(packLighting(source,"GetLighting","worldPos",bsl)
            && packLighting(source,"GetLighting","worldPos",bslAlbedo)
            && parameters(source,"GetLighting","inout\\s+vec3\\s+albedo", "vec3\\s+normal", "float\\s+emission", "vec2\\s+lightmap")
            && bslAlbedo.start()>bsl.end()) {
            String replacement=bsl.group()+"\nvec4 prismIncident = PrismSurfaceLight(worldPos, normalize(mat3(prismInverseView) * normal));\n"
                +"float prismExposure = PrismLocalSunExposure(worldPos + normalize(mat3(prismInverseView) * normal) * 0.5, PrismSunExposure(lightmap.y / 0.9333333));\n"
                +"blockLighting = PrismBlockLighting(blockLighting, prismIncident, prismExposure);\n";
            String adaptation="sceneLighting = PrismAmbientLighting(sceneLighting, prismIncident, prismExposure);\n"
                +"minLighting = PrismAmbientLighting(minLighting, prismIncident, prismExposure);\n"
                +"albedo = PrismLightReadyAlbedo(albedo, prismIncident.rgb, clamp(prismIncident.a, 0.0, 1.0) * (1.0 - prismExposure) * (1.0 - clamp(emission, 0.0, 1.0)), prismExposure);\n";
            String result=source.substring(0,bsl.start())+replacement+source.substring(bsl.end(),bslAlbedo.start())
                +adaptation+source.substring(bslAlbedo.start());
            int function=functionStart(result,"GetLighting");
            return result.substring(0,function)+COMMON+"\n"+result.substring(function);
        }
        Matcher coreTinted=CORE_TINTED_LIGHT.matcher(source);
        if(coreTinted.find() && positionReady(source,coreTinted.start())) {
            String result=coreTinted.replaceAll(match->Matcher.quoteReplacement(
                "PrismLitVertex(Color, "+match.group(1)+", gl_Position, float(UV2.y) / 240.0)"));
            return insertBeforeMain(result);
        }
        Matcher sodiumTinted=SODIUM_TINTED_LIGHT.matcher(source);
        if(sodiumTinted.find() && positionReady(source,sodiumTinted.start())) {
            String result=sodiumTinted.replaceAll(match->Matcher.quoteReplacement(
                "PrismLitVertex(_vert_color, "+match.group(1)+", gl_Position, (_vert_tex_light_coord.y * 256.0 - 8.0) / 240.0)"));
            return insertBeforeMain(result);
        }
        Matcher core=CORE_LIGHT.matcher(source);
        if(core.find() && positionReady(source,core.start())) {
            String result=core.replaceAll(match->Matcher.quoteReplacement("PrismLightmap("+match.group()+", gl_Position, float(UV2.y) / 240.0)"));
            return insertBeforeMain(result);
        }
        int sodiumSample=source.indexOf("texture(u_LightTex, _vert_tex_light_coord)");
        if(sodiumSample>=0 && positionReady(source,sodiumSample)) {
            return insertBeforeMain(source.replace("texture(u_LightTex, _vert_tex_light_coord)",
                "PrismLightmap(texture(u_LightTex, _vert_tex_light_coord), gl_Position, (_vert_tex_light_coord.y * 256.0 - 8.0) / 240.0)"));
        }
        return source;
    }
    private static String insertBeforeMain(String source) {
        Matcher main=MAIN.matcher(source);
        if(!main.find()) return source;
        return source.substring(0,main.start())+COMMON+"\n"+source.substring(main.start());
    }
    private static boolean positionReady(String source,int sample) {
        Matcher main=MAIN.matcher(source);
        return main.find() && main.start()<sample
            && CLIP_ASSIGNMENT.matcher(source).region(main.end(),sample).find();
    }
    private static int functionStart(String source,String function) {
        Matcher signature=Pattern.compile("\\bvoid\\s+"+function+"\\s*\\(").matcher(source);
        return signature.find()?signature.start():-1;
    }
    private static boolean parameters(String source,String function,String... required) {
        Matcher signature=Pattern.compile("\\bvoid\\s+"+function+"\\s*\\(([^)]*)\\)").matcher(source);
        if(!signature.find()) return false;
        for(String parameter:required)
            if(!Pattern.compile("\\b"+parameter+"\\b").matcher(signature.group(1)).find()) return false;
        return true;
    }
    private static boolean packLighting(String source,String function,String position,Matcher light) {
        // A token in some OTHER function does not establish a safe insertion
        // point. Only adapt one recognized light expression in this body.
        Matcher signature=Pattern.compile("\\bvoid\\s+"+function+"\\s*\\([^)]*\\bvec3\\s+"+position+"\\b[^)]*\\)\\s*\\{").matcher(source);
        if(!signature.find()) return false;
        int start=signature.end(), end=start, depth=1;
        while(end<source.length() && depth>0) {
            char c=source.charAt(end++);
            if(c=='{') depth++;
            if(c=='}') depth--;
        }
        if(depth!=0 || signature.find()) return false;
        light.region(start,end-1);
        if(!light.find()) return false;
        int match=light.start();
        if(light.find()) return false;
        // Restore the matched expression for group()/replaceFirst().
        light.region(start,end-1);
        return light.find() && light.start()==match;
    }
}
