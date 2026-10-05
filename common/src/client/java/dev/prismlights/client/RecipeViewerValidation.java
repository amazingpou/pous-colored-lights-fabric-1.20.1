package dev.prismlights.client;

import dev.prismlights.PrismLights;
import dev.prismlights.platform.PlatformServices;
import net.minecraft.recipe.Recipe;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;

/** Reflection is restricted to the optional, opt-in JEI QA helper. */
final class RecipeViewerValidation {
    private RecipeViewerValidation() {}
    static void verify() {
        if(!PlatformServices.get().isModLoaded("jei")) return;
        try {
            Object runtime=Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
            Class<?> api=Class.forName("mezz.jei.api.runtime.IJeiRuntime");
            Object manager=api.getMethod("getRecipeManager").invoke(runtime);
            Object type=Class.forName("mezz.jei.api.constants.RecipeTypes").getField("CRAFTING").get(null);
            Object lookup=Class.forName("mezz.jei.api.recipe.IRecipeManager")
                .getMethod("createRecipeLookup",Class.forName("mezz.jei.api.recipe.RecipeType")).invoke(manager,type);
            long count;
            try(Stream<?> stream=(Stream<?>)Class.forName("mezz.jei.api.recipe.IRecipeLookup").getMethod("get").invoke(lookup)) {
                count=stream.filter(r->r instanceof Recipe<?> recipe && recipe.getId().getNamespace().equals(PrismLights.ID)).count();
            }
            if(count!=113) throw new AssertionError("JEI crafting index expected 113 mod recipes, found "+count);
            Object filter=api.getMethod("getIngredientFilter").invoke(runtime);
            Class.forName("mezz.jei.api.runtime.IIngredientFilter").getMethod("setFilterText",String.class).invoke(filter,"@prism_lights");
            LoggerFactory.getLogger("Prism Lights").info("JEI QA: 113 mod crafting recipes indexed PASS");
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("JEI native runtime verification failed",error);}
    }
}
