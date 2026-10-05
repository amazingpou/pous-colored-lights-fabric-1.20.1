package dev.prismlights;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderPatcherTest {
    @Test void complementaryReplacesTheFinalBlockLightBeforeDiffuseMaterialShading() {
        String source="""
            #version 330 compatibility
            void DoLighting(inout vec4 color, vec3 playerPos, vec3 worldGeoNormal, float emission, vec2 lightmap) {
                vec3 blockLighting = lightmapXM * blocklightCol;
                blockLighting = mix(specialLighting, blockLighting, blocklightDecider);
                vec3 finalDiffuse = pow2(directionShade * vanillaAO) * (blockLighting + pow2(sceneLighting) + minLighting) + pow2(emission);
                color.rgb *= sqrt(finalDiffuse);
            }
            void main() {}
            """;
        String result=ShaderPatcher.patch(source);
        assertTrue(result.indexOf("PrismSurfaceLight(playerPos,")>result.indexOf("blockLighting = mix(specialLighting"));
        assertTrue(result.indexOf("PrismSurfaceLight(playerPos,")<result.indexOf("vec3 finalDiffuse"));
        assertTrue(result.contains("color.rgb = PrismLightReadyAlbedo(color.rgb"));
        assertTrue(result.contains("PrismSurfaceLight(playerPos, normalize(worldGeoNormal))"));
        assertTrue(result.contains("blockLighting = PrismBlockLighting(blockLighting, prismIncident, prismExposure)"));
        assertTrue(result.contains("float prismExposure = PrismLocalSunExposure("));
        assertTrue(result.contains("PrismSunExposure(lightmap.y)"));
        assertFalse(result.contains("float prismExposure = PrismVertexSunExposure(lightmap.y)"));
        assertFalse(result.contains("#ifdef GBUFFERS_TERRAIN"));
        assertEquals(result,ShaderPatcher.patch(result));
    }
    @Test void bslChangesIncidentBlockLightingBeforeAlbedoForEveryMaterial() {
        String material="""
            #version 330 compatibility
            void GetLighting(inout vec3 albedo, vec3 worldPos, vec3 normal, float emission, vec2 lightmap) {
                vec3 blockLighting = blocklightCol * newLightmap * newLightmap;
                vec3 emissiveLighting = normalize(albedo);
                albedo *= max(sceneLighting + blockLighting + emissiveLighting + minLighting, vec3(0));
            }
            void main() {}
            """;
        String result=ShaderPatcher.patch(material);
        assertTrue(result.indexOf("PrismSurfaceLight(worldPos,")<result.indexOf("albedo *= max(sceneLighting"));
        assertTrue(result.indexOf("albedo = PrismLightReadyAlbedo(albedo")>result.indexOf("vec3 emissiveLighting"));
        assertTrue(result.contains("PrismSurfaceLight(worldPos, normalize(mat3(prismInverseView) * normal))"));
        assertTrue(result.contains("sceneLighting + blockLighting"));
        assertFalse(result.contains("gl_FragColor *= "));
        assertEquals(result,ShaderPatcher.patch(result));
    }
    @Test void coreAndSodiumReplaceOnlyTheLightmapSample() {
        String core="#version 150\nvoid main() {gl_Position=vec4(0); vertexColor=Color*minecraft_sample_lightmap(Sampler2, UV2);}";
        String sodium="#version 330\nvoid main() {gl_Position=vec4(0); v_Color=_vert_color*texture(u_LightTex, _vert_tex_light_coord);}";
        String entity="#version 150\nvoid main() {gl_Position=vec4(0); lightMapColor=texelFetch(Sampler2, UV2 / 16, 0);}";
        assertTrue(ShaderPatcher.patch(core).contains("vertexColor=PrismLitVertex(Color, minecraft_sample_lightmap(Sampler2, UV2), gl_Position, float(UV2.y) / 240.0)"));
        assertTrue(ShaderPatcher.patch(sodium).contains("v_Color=PrismLitVertex(_vert_color, texture(u_LightTex, _vert_tex_light_coord), gl_Position, (_vert_tex_light_coord.y * 256.0 - 8.0) / 240.0)"));
        assertTrue(ShaderPatcher.patch(core).startsWith("#version 150"));
        assertTrue(ShaderPatcher.patch(entity).contains("lightMapColor=PrismLightmap(texelFetch(Sampler2, UV2 / 16, 0), gl_Position, float(UV2.y) / 240.0)"));
    }
    @Test void tintedTerrainKeepsTextureDetailButAllowsColoredIrradiance() {
        String source="#version 150\nvoid main(){gl_Position=vec4(0);vertexColor=Color * minecraft_sample_lightmap(Sampler2, UV2);}";
        String result=ShaderPatcher.patch(source);
        assertTrue(result.contains("PrismLightReadyAlbedo"));
        assertTrue(result.contains("coverage * mix(1.20, 0.12, exposure)"));
        assertTrue(result.contains("chroma * coverage * 0.12"));
        assertTrue(result.contains("PrismDisplayRadiance(incident.rgb / max(coverage, 0.001))"));
        assertTrue(result.contains("float exposure = PrismLocalSunExposure("));
        assertTrue(result.contains("PrismVertexSunExposure(skyLight)"));
        assertTrue(result.contains("PrismLightReadyAlbedo(tint.rgb, incident.rgb, coverage, exposure) * lit.rgb"));
        assertTrue(result.contains("max(linearRgb, vec3(0.0)) / max(1.0, peak)"));
        assertEquals(result,ShaderPatcher.patch(result));
    }
    @Test void unsupportedShadersStayUntouchedRatherThanReceivingAnOverlay() {
        String composite="#version 330\nvoid main(){gl_FragColor=texture(colortex0,uv);}";
        assertEquals(composite,ShaderPatcher.patch(composite));
    }
    @Test void incompleteOrReorderedVertexProgramsRemainIntact() {
        String noMain="gl_Position = vec4(0); Color * minecraft_sample_lightmap(Sampler2, UV2);";
        String latePosition="void main(){vertexColor=Color * minecraft_sample_lightmap(Sampler2, UV2); gl_Position=vec4(0);}";
        assertEquals(noMain,ShaderPatcher.patch(noMain));
        assertEquals(latePosition,ShaderPatcher.patch(latePosition));
    }
    @Test void packTokensInDifferentFunctionsDoNotTriggerAnUnsafePatch() {
        String source="void DoLighting(vec3 unrelated) {} void other(vec3 playerPos) {vec3 finalDiffuse=blockLighting;}";
        assertEquals(source,ShaderPatcher.patch(source));
        String ambiguous="void GetLighting(vec3 worldPos) {vec3 blockLighting=blocklightCol*x; {vec3 blockLighting=blocklightCol*y;}}";
        assertEquals(ambiguous,ShaderPatcher.patch(ambiguous));
    }
    @Test void lightingExpressionOutsideTheRecognizedFunctionIsPreserved() {
        String other="void other(){vec3 blockLighting=blocklightCol*outside;}";
        String source=other+"\nvoid GetLighting(inout vec3 albedo, vec3 worldPos, vec3 normal, float emission, vec2 lightmap){vec3 blockLighting=blocklightCol*inside; albedo *= max(sceneLighting + blockLighting + minLighting, vec3(0));}";
        String result=ShaderPatcher.patch(source);
        assertTrue(result.startsWith(other));
        assertTrue(result.indexOf("PrismSurfaceLight(worldPos,")>result.indexOf("blocklightCol*inside"));
    }
    @Test void packAdaptationRequiresMaterialNormalAndEmissionInTheSameSignature() {
        String bsl="void GetLighting(inout vec3 albedo, vec3 worldPos, float emission) {vec3 blockLighting=blocklightCol*x; albedo *= max(sceneLighting + blockLighting + minLighting, vec3(0));}";
        assertEquals(bsl,ShaderPatcher.patch(bsl));
        String complementary="void DoLighting(inout vec4 color, vec3 playerPos, vec3 worldGeoNormal) {vec3 finalDiffuse=blockLighting + pow2(sceneLighting) + minLighting;}";
        assertEquals(complementary,ShaderPatcher.patch(complementary));
    }
    @Test void bslWithoutTheRecognizedMaterialApplicationIsNotPartiallyModified() {
        String source="void GetLighting(inout vec3 albedo, vec3 worldPos, vec3 normal, float emission) {vec3 blockLighting=blocklightCol*x; albedo *= unknownLighting;}";
        assertEquals(source,ShaderPatcher.patch(source));
    }
    @Test void packWithoutLocalSkylightParameterRemainsIntact() {
        String source="void DoLighting(inout vec4 color, vec3 playerPos, vec3 worldGeoNormal, float emission){vec3 finalDiffuse=blockLighting + sceneLighting + minLighting;}";
        assertEquals(source,ShaderPatcher.patch(source));
    }
}
